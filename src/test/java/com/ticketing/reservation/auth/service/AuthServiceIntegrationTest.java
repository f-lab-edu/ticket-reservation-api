package com.ticketing.reservation.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.ticketing.reservation.TestcontainersConfiguration;
import com.ticketing.reservation.auth.config.AuthProperties;
import com.ticketing.reservation.auth.config.RefreshTokenProperties;
import com.ticketing.reservation.auth.domain.RefreshToken;
import com.ticketing.reservation.auth.domain.RefreshTokenStatus;
import com.ticketing.reservation.auth.dto.TokenResponse;
import com.ticketing.reservation.auth.repository.RefreshTokenRepository;
import com.ticketing.reservation.auth.token.AccessTokenProvider;
import com.ticketing.reservation.auth.token.RefreshTokenProvider;
import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import com.ticketing.reservation.member.domain.Member;
import com.ticketing.reservation.member.repository.MemberRepository;
import com.ticketing.reservation.member.service.MemberService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
class AuthServiceIntegrationTest {

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

  @Test
  @DisplayName("인증 정보가 맞으면 토큰을 발급하고 리프레시 토큰은 해시값으로 저장한다")
  void issuesTokensWhenCredentialsAreValid() {
    // given
    String email = " User@Example.com ";

    // when
    TokenResponse tokens = authService.login(email, PASSWORD);

    // then
    Jwt jwt = jwtDecoder.decode(tokens.accessToken());
    assertThat(jwt.getSubject()).isEqualTo(String.valueOf(member.getId()));
    assertThat(jwt.getClaimAsString(AccessTokenProvider.ROLE_CLAIM)).isEqualTo("USER");
    assertThat(tokens.tokenType()).isEqualTo("Bearer");
    assertThat(tokens.expiresIn()).isEqualTo(properties.accessTokenValidity().toSeconds());
    assertThat(tokens.refreshToken()).isNotBlank();
    assertThat(stored(tokens.refreshToken()))
        .satisfies(
            token -> {
              assertThat(token.memberId()).isEqualTo(member.getId());
              assertThat(token.status()).isEqualTo(RefreshTokenStatus.ACTIVE);
              assertThat(token.expiresAt())
                  .isEqualTo(token.createdAt().plus(refreshTokenProperties.validity()));
            });
    assertThat(redisTemplate.hasKey("auth:refresh-token:" + tokens.refreshToken())).isFalse();
  }

  @Test
  @DisplayName("로그인할 때마다 새로운 Token Family로 발급한다")
  void issuesDifferentTokenFamiliesForEachLogin() {
    // when
    TokenResponse first = authService.login(EMAIL, PASSWORD);
    TokenResponse second = authService.login(EMAIL, PASSWORD);

    // then
    assertThat(stored(first.refreshToken()).familyId())
        .isNotEqualTo(stored(second.refreshToken()).familyId());
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
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
  }

  private RefreshToken stored(String rawToken) {
    return refreshTokenRepository
        .findByTokenHash(refreshTokenProvider.hash(rawToken))
        .orElseThrow();
  }
}
