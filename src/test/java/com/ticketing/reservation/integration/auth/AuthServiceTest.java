package com.ticketing.reservation.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.ticketing.reservation.auth.config.AuthProperties;
import com.ticketing.reservation.auth.config.RefreshTokenProperties;
import com.ticketing.reservation.auth.domain.RefreshToken;
import com.ticketing.reservation.auth.domain.RefreshTokenStatus;
import com.ticketing.reservation.auth.dto.TokenResponse;
import com.ticketing.reservation.auth.repository.RefreshTokenRepository;
import com.ticketing.reservation.auth.service.AuthService;
import com.ticketing.reservation.auth.token.AccessTokenProvider;
import com.ticketing.reservation.auth.token.RefreshTokenProvider;
import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import com.ticketing.reservation.integration.TestcontainersConfiguration;
import com.ticketing.reservation.member.domain.Member;
import com.ticketing.reservation.member.repository.MemberRepository;
import com.ticketing.reservation.member.service.MemberService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AuthServiceTest {

  private static final String EMAIL = "user@example.com";
  private static final String PASSWORD = "password";

  @Autowired private AuthService authService;
  @Autowired private MemberService memberService;
  @Autowired private MemberRepository memberRepository;
  @Autowired private JwtDecoder jwtDecoder;
  @Autowired private AuthProperties properties;
  @Autowired private RefreshTokenProperties refreshTokenProperties;
  @Autowired private RefreshTokenProvider refreshTokenProvider;
  @Autowired private RefreshTokenRepository refreshTokenRepository;
  @Autowired private StringRedisTemplate redisTemplate;

  private Member member;

  @BeforeEach
  void setUp() {
    member = memberService.signUp(EMAIL, PASSWORD);
  }

  @AfterEach
  void tearDown() {
    memberRepository.deleteAllInBatch();
  }

  @Nested
  @DisplayName("로그인")
  class Login {

    @Test
    @DisplayName("인증 정보가 맞으면 토큰을 발급하고 리프레시 토큰은 해시값으로 저장한다")
    void issuesTokensWhenCredentialsAreValid() {
      // given
      String email = " User@Example.com ";

      // when
      TokenResponse tokens = authService.login(email, PASSWORD);

      // then
      Jwt jwt = jwtDecoder.decode(tokens.accessToken());
      RefreshToken storedToken = getStoredToken(tokens.refreshToken());

      assertThat(jwt.getSubject()).isEqualTo(String.valueOf(member.getId()));
      assertThat(jwt.getClaimAsString(AccessTokenProvider.ROLE_CLAIM)).isEqualTo("USER");
      assertThat(jwt.getClaimAsString(AccessTokenProvider.SESSION_CLAIM))
          .isEqualTo(storedToken.familyId());
      assertThat(tokens.tokenType()).isEqualTo("Bearer");
      assertThat(tokens.expiresIn()).isEqualTo(properties.accessTokenValidity().toSeconds());
      assertThat(tokens.refreshToken()).isNotBlank();
      assertThat(storedToken.memberId()).isEqualTo(member.getId());
      assertThat(storedToken.status()).isEqualTo(RefreshTokenStatus.ACTIVE);
      assertThat(storedToken.expiresAt())
          .isEqualTo(storedToken.createdAt().plus(refreshTokenProperties.validity()));
      assertThat(redisTemplate.hasKey("auth:refresh-token:" + tokens.refreshToken())).isFalse();
    }

    @Test
    @DisplayName("로그인할 때마다 새로운 토큰 패밀리로 발급한다")
    void issuesDifferentTokenFamiliesForEachLogin() {
      // when
      TokenResponse first = authService.login(EMAIL, PASSWORD);
      TokenResponse second = authService.login(EMAIL, PASSWORD);

      // then
      assertThat(getStoredToken(first.refreshToken()).familyId())
          .isNotEqualTo(getStoredToken(second.refreshToken()).familyId());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
      "존재하지 않는 이메일, unknown@example.com, password",
      "비밀번호 불일치, user@example.com, wrong-password"
    })
    @DisplayName("인증 정보가 틀리면 로그인을 거부한다")
    void rejectsLoginWithInvalidCredentials(String description, String email, String password) {
      // when
      Throwable thrown = catchThrowable(() -> authService.login(email, password));

      // then
      assertThat(thrown).satisfies(hasErrorCode(ErrorCode.INVALID_CREDENTIALS));
    }
  }

  @Nested
  @DisplayName("토큰 재발급")
  class Refresh {

    @Test
    @DisplayName("유효한 리프레시 토큰이면 새 토큰 쌍을 발급하고 기존 토큰을 사용 처리한다")
    void issuesNewTokenPairAndMarksCurrentTokenUsed() {
      // given
      TokenResponse login = authService.login(EMAIL, PASSWORD);

      // when
      TokenResponse refreshed = authService.refresh(login.refreshToken());

      // then
      Jwt jwt = jwtDecoder.decode(refreshed.accessToken());
      RefreshToken usedToken = getStoredToken(login.refreshToken());
      RefreshToken newToken = getStoredToken(refreshed.refreshToken());

      assertThat(jwt.getSubject()).isEqualTo(String.valueOf(member.getId()));
      assertThat(jwt.getClaimAsString(AccessTokenProvider.ROLE_CLAIM)).isEqualTo("USER");
      assertThat(jwt.getClaimAsString(AccessTokenProvider.SESSION_CLAIM))
          .isEqualTo(usedToken.familyId());
      assertThat(refreshed.refreshToken()).isNotEqualTo(login.refreshToken());
      assertThat(usedToken.status()).isEqualTo(RefreshTokenStatus.USED);
      assertThat(newToken.status()).isEqualTo(RefreshTokenStatus.ACTIVE);
    }

    @Test
    @DisplayName("만료된 리프레시 토큰이면 재발급을 거부한다")
    void rejectsExpiredToken() {
      // given
      TokenResponse login = authService.login(EMAIL, PASSWORD);
      redisTemplate
          .opsForHash()
          .put(
              "auth:refresh-token:" + refreshTokenProvider.hash(login.refreshToken()),
              "expiresAt",
              "1");

      // when
      Throwable thrown = catchThrowable(() -> authService.refresh(login.refreshToken()));

      // then
      assertThat(thrown).satisfies(hasErrorCode(ErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    @DisplayName("존재하지 않는 리프레시 토큰이면 재발급을 거부한다")
    void rejectsUnknownToken() {
      // given
      String unknown = refreshTokenProvider.generate();

      // when
      Throwable thrown = catchThrowable(() -> authService.refresh(unknown));

      // then
      assertThat(thrown).satisfies(hasErrorCode(ErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    @DisplayName("이미 사용한 리프레시 토큰이면 재발급을 거부하고 같은 로그인 세션의 토큰을 모두 무효화한다")
    void rejectsUsedTokenAndRevokesFamily() {
      // given
      String initialToken = authService.login(EMAIL, PASSWORD).refreshToken();
      String rotatedToken = authService.refresh(initialToken).refreshToken();

      // when
      Throwable reusedTokenThrown = catchThrowable(() -> authService.refresh(initialToken));
      Throwable revokedTokenThrown = catchThrowable(() -> authService.refresh(rotatedToken));

      // then
      assertThat(reusedTokenThrown).satisfies(hasErrorCode(ErrorCode.INVALID_REFRESH_TOKEN));
      assertThat(revokedTokenThrown).satisfies(hasErrorCode(ErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    @DisplayName("재사용이 감지돼도 다른 로그인 세션의 토큰은 재발급할 수 있다")
    void keepsOtherLoginsWhenReuseIsDetected() {
      // given
      String deviceAToken = authService.login(EMAIL, PASSWORD).refreshToken();
      String deviceBToken = authService.login(EMAIL, PASSWORD).refreshToken();
      authService.refresh(deviceAToken);

      // when
      Throwable deviceAReusedThrown = catchThrowable(() -> authService.refresh(deviceAToken));
      TokenResponse deviceBResponse = authService.refresh(deviceBToken);

      // then
      assertThat(deviceAReusedThrown).satisfies(hasErrorCode(ErrorCode.INVALID_REFRESH_TOKEN));
      assertThat(deviceBResponse.refreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("같은 리프레시 토큰으로 동시에 요청하면 재사용으로 판단해 성공한 요청이 받은 토큰까지 무효화한다")
    void revokesFamilyOnConcurrentRefresh() throws Exception {
      // given
      String initialToken = authService.login(EMAIL, PASSWORD).refreshToken();

      // when
      List<TokenResponse> responses = runConcurrently(10, () -> refreshOrNull(initialToken));

      // then
      List<TokenResponse> successResponses = responses.stream().filter(Objects::nonNull).toList();
      assertThat(successResponses).hasSize(1);

      String rotatedToken = successResponses.getFirst().refreshToken();
      Throwable revokedTokenThrown = catchThrowable(() -> authService.refresh(rotatedToken));
      assertThat(revokedTokenThrown).satisfies(hasErrorCode(ErrorCode.INVALID_REFRESH_TOKEN));
    }
  }

  @Nested
  @DisplayName("로그아웃")
  class Logout {

    @Test
    @DisplayName("로그아웃하면 같은 로그인 세션을 무효화해 이후 재발급을 거부한다")
    void revokesLoginSession() {
      // given
      String refreshToken = authService.login(EMAIL, PASSWORD).refreshToken();
      String familyId = getStoredToken(refreshToken).familyId();

      // when
      authService.logout(familyId);

      // then
      assertThat(refreshTokenRepository.isFamilyRevoked(familyId)).isTrue();
      Throwable thrown = catchThrowable(() -> authService.refresh(refreshToken));
      assertThat(thrown).satisfies(hasErrorCode(ErrorCode.INVALID_REFRESH_TOKEN));
    }
  }

  private static <T> List<T> runConcurrently(int threadCount, Callable<T> task) throws Exception {
    CountDownLatch ready = new CountDownLatch(threadCount);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<T>> futures = new ArrayList<>();

    try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
      for (int i = 0; i < threadCount; i++) {
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  return task.call();
                }));
      }
      ready.await();
      start.countDown();
    }

    List<T> results = new ArrayList<>();
    for (Future<T> future : futures) {
      results.add(future.get());
    }
    return results;
  }

  private TokenResponse refreshOrNull(String refreshToken) {
    try {
      return authService.refresh(refreshToken);
    } catch (BusinessException e) {
      if (e.getErrorCode() != ErrorCode.INVALID_REFRESH_TOKEN) {
        throw e;
      }
      return null;
    }
  }

  private RefreshToken getStoredToken(String rawToken) {
    return refreshTokenRepository
        .findByTokenHash(refreshTokenProvider.hash(rawToken))
        .orElseThrow();
  }

  private static ThrowingConsumer<Throwable> hasErrorCode(ErrorCode errorCode) {
    return thrown ->
        assertThat(thrown)
            .isInstanceOfSatisfying(
                BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
  }
}
