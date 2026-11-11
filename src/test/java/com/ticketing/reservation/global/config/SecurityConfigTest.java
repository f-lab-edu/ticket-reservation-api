package com.ticketing.reservation.global.config;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketing.reservation.auth.config.AuthProperties;
import com.ticketing.reservation.auth.config.JwtConfig;
import com.ticketing.reservation.auth.repository.RefreshTokenRepository;
import com.ticketing.reservation.auth.token.AccessTokenProvider;
import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import com.ticketing.reservation.member.domain.Member;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(SecurityConfigTest.TestController.class)
@Import({SecurityConfig.class, JwtConfig.class, SecurityConfigTest.TestController.class})
class SecurityConfigTest {

  private static final String PROTECTED_PATH = "/test/protected";
  private static final String PUBLIC_PATH = "/api/v1/auth/login";
  private static final Duration DEFAULT_CLOCK_SKEW = Duration.ofSeconds(60);
  private static final String FAMILY_ID = "family";

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtEncoder jwtEncoder;
  @Autowired private AuthProperties properties;

  @MockitoBean private RefreshTokenRepository refreshTokenRepository;

  @Test
  @DisplayName("유효한 토큰이면 회원 식별자와 권한으로 인증된다")
  void authenticatesWithValidAccessToken() throws Exception {
    // given
    String token = providerAt(Instant.now()).issue(member(7L), FAMILY_ID);

    // when
    ResultActions result =
        mockMvc.perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

    // then
    result
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("7"))
        .andExpect(jsonPath("$.authorities[0]").value("ROLE_USER"));
  }

  @Test
  @DisplayName("토큰이 없으면 401을 반환한다")
  void returnsUnauthorizedWithoutAccessToken() throws Exception {
    // when
    ResultActions result = mockMvc.perform(get(PROTECTED_PATH));

    // then
    result.andExpect(unauthorizedProblemDetail());
  }

  @Test
  @DisplayName("만료된 토큰이면 401을 반환한다")
  void returnsUnauthorizedWithInvalidAccessToken() throws Exception {
    // given
    String token = expiredToken();

    // when
    ResultActions result =
        mockMvc.perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

    // then
    result.andExpect(unauthorizedProblemDetail());
  }

  @Test
  @DisplayName("공개 API는 만료된 토큰이어도 허용한다")
  void ignoresAccessTokenOnPublicEndpoint() throws Exception {
    // given
    String token = expiredToken();

    // when
    ResultActions result =
        mockMvc.perform(post(PUBLIC_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

    // then
    result.andExpect(status().isOk());
  }

  @Test
  @DisplayName("무효화된 로그인 세션의 토큰이면 401을 반환한다")
  void returnsUnauthorizedWhenSessionIsRevoked() throws Exception {
    // given
    String token = providerAt(Instant.now()).issue(member(7L), FAMILY_ID);
    given(refreshTokenRepository.isFamilyRevoked(FAMILY_ID)).willReturn(true);

    // when
    ResultActions result =
        mockMvc.perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

    // then
    result.andExpect(unauthorizedProblemDetail());
  }

  @Test
  @DisplayName("로그인 세션 정보가 없는 토큰이면 401을 반환한다")
  void returnsUnauthorizedWithoutSessionClaim() throws Exception {
    // given
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject("7")
            .claim(AccessTokenProvider.ROLE_CLAIM, "USER")
            .issuedAt(now)
            .expiresAt(now.plus(properties.accessTokenValidity()))
            .build();
    String token =
        jwtEncoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();

    // when
    ResultActions result =
        mockMvc.perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

    // then
    result.andExpect(unauthorizedProblemDetail());
  }

  @Test
  @DisplayName("로그인 세션을 확인할 수 없으면 503을 반환한다")
  void returnsServiceUnavailableWhenSessionCannotBeChecked() throws Exception {
    // given
    String token = providerAt(Instant.now()).issue(member(7L), FAMILY_ID);
    given(refreshTokenRepository.isFamilyRevoked(FAMILY_ID))
        .willThrow(new BusinessException(ErrorCode.AUTH_STORE_UNAVAILABLE));

    // when
    ResultActions result =
        mockMvc.perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

    // then
    result
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.instance").value(PROTECTED_PATH))
        .andExpect(jsonPath("$.code").value("AUTH_STORE_UNAVAILABLE"));
  }

  private ResultMatcher unauthorizedProblemDetail() {
    return result -> {
      status().isUnauthorized().match(result);
      content().contentType(MediaType.APPLICATION_PROBLEM_JSON).match(result);
      jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()).match(result);
      jsonPath("$.instance").value(PROTECTED_PATH).match(result);
      jsonPath("$.code").value("UNAUTHORIZED").match(result);
    };
  }

  private String expiredToken() {
    Instant issuedAt =
        Instant.now()
            .minus(properties.accessTokenValidity())
            .minus(DEFAULT_CLOCK_SKEW)
            .minusSeconds(1);
    return providerAt(issuedAt).issue(member(7L), FAMILY_ID);
  }

  private AccessTokenProvider providerAt(Instant now) {
    return new AccessTokenProvider(jwtEncoder, properties, Clock.fixed(now, ZoneOffset.UTC));
  }

  private Member member(Long id) {
    Member member = Member.create("user@example.com", "{bcrypt}hash");
    ReflectionTestUtils.setField(member, "id", id);
    return member;
  }

  @RestController
  static class TestController {

    @GetMapping(PROTECTED_PATH)
    Map<String, Object> me(Authentication authentication) {
      return Map.of(
          "name",
          authentication.getName(),
          "authorities",
          authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
    }

    @PostMapping(PUBLIC_PATH)
    void publicEndpoint() {
      // 보안 필터 통과 확인용 빈 핸들러
    }
  }
}
