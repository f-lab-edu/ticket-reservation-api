package com.ticketing.reservation.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketing.reservation.auth.config.AuthProperties;
import com.ticketing.reservation.auth.config.JwtConfig;
import com.ticketing.reservation.auth.token.AccessTokenProvider;
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
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(SecurityConfigTest.TestController.class)
@Import({SecurityConfig.class, JwtConfig.class, SecurityConfigTest.TestController.class})
class SecurityConfigTest {

  private static final String PROTECTED_PATH = "/test/protected";
  private static final String PUBLIC_PATH = "/api/v1/auth/login";

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtEncoder jwtEncoder;
  @Autowired private AuthProperties properties;

  @Test
  @DisplayName("유효한 액세스 토큰이면 회원 식별자와 권한으로 인증된다")
  void authenticatesWithValidAccessToken() throws Exception {
    String token = providerAt(Instant.now()).issue(member(7L));

    mockMvc
        .perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("7"))
        .andExpect(jsonPath("$.authorities[0]").value("ROLE_USER"));
  }

  @Test
  @DisplayName("액세스 토큰이 없으면 401과 UNAUTHORIZED 오류 응답을 반환한다")
  void returnsUnauthorizedWithoutAccessToken() throws Exception {
    mockMvc.perform(get(PROTECTED_PATH)).andExpect(unauthorizedProblemDetail());
  }

  @Test
  @DisplayName("액세스 토큰이 유효하지 않으면 401과 UNAUTHORIZED 오류 응답을 반환한다")
  void returnsUnauthorizedWithInvalidAccessToken() throws Exception {
    mockMvc
        .perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken()))
        .andExpect(unauthorizedProblemDetail());
  }

  @Test
  @DisplayName("공개 API는 만료된 액세스 토큰을 보내도 거절하지 않는다")
  void ignoresAccessTokenOnPublicEndpoint() throws Exception {
    mockMvc
        .perform(post(PUBLIC_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken()))
        .andExpect(status().isOk());
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
        Instant.now().minus(properties.accessTokenValidity()).minus(Duration.ofMinutes(2));
    return providerAt(issuedAt).issue(member(7L));
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
