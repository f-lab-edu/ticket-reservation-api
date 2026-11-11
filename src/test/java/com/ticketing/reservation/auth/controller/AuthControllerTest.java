package com.ticketing.reservation.auth.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketing.reservation.auth.config.AuthProperties;
import com.ticketing.reservation.auth.config.JwtConfig;
import com.ticketing.reservation.auth.dto.TokenResponse;
import com.ticketing.reservation.auth.repository.RefreshTokenRepository;
import com.ticketing.reservation.auth.service.AuthService;
import com.ticketing.reservation.auth.token.AccessTokenProvider;
import com.ticketing.reservation.global.config.SecurityConfig;
import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import com.ticketing.reservation.member.domain.Member;
import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class AuthControllerTest {

  private static final TokenResponse TOKENS =
      TokenResponse.bearer("access-token", 1800, "refresh-token");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtEncoder jwtEncoder;
  @Autowired private AuthProperties properties;

  @MockitoBean private AuthService authService;
  @MockitoBean private RefreshTokenRepository refreshTokenRepository;

  @Nested
  @DisplayName("로그인")
  class Login {

    @Test
    @DisplayName("로그인에 성공하면 200과 토큰을 반환한다")
    void returnsTokensWhenLoginSucceeds() throws Exception {
      // given
      given(authService.login("user@example.com", "password")).willReturn(TOKENS);

      // when
      ResultActions result =
          mockMvc.perform(
              post("/api/v1/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"email": "user@example.com", "password": "password"}
                      """));

      // then
      result
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.tokenType").value("Bearer"))
          .andExpect(jsonPath("$.accessToken").value("access-token"))
          .andExpect(jsonPath("$.expiresIn").value(1800))
          .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
    }

    @Test
    @DisplayName("로그인에 실패하면 401을 반환한다")
    void returnsUnauthorizedWhenLoginFails() throws Exception {
      // given
      given(authService.login("user@example.com", "wrong"))
          .willThrow(new BusinessException(ErrorCode.INVALID_CREDENTIALS));

      // when
      ResultActions result =
          mockMvc.perform(
              post("/api/v1/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"email": "user@example.com", "password": "wrong"}
                      """));

      // then
      result
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @ParameterizedTest
    @DisplayName("유효하지 않은 요청값이면 400을 반환한다")
    @ValueSource(
        strings = {
          """
          {"email": " ", "password": "password"}
          """,
          """
          {"email": "user@example.com", "password": ""}
          """,
          """
          {"email": "user@example.com", "password": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
          """
        })
    void returnsBadRequestWhenLoginRequestIsInvalid(String body) throws Exception {
      // when
      ResultActions result =
          mockMvc.perform(
              post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));

      // then
      result.andExpect(status().isBadRequest());
      then(authService).shouldHaveNoInteractions();
    }
  }

  @Nested
  @DisplayName("토큰 재발급")
  class Refresh {

    @Test
    @DisplayName("재발급에 성공하면 200과 새 토큰을 반환한다")
    void returnsTokensWhenRefreshSucceeds() throws Exception {
      // given
      given(authService.refresh("refresh-token")).willReturn(TOKENS);

      // when
      ResultActions result =
          mockMvc.perform(
              post("/api/v1/auth/refresh")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"refreshToken": "refresh-token"}
                      """));

      // then
      result
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.accessToken").value("access-token"))
          .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
    }

    @ParameterizedTest
    @DisplayName("리프레시 토큰이 비어 있으면 400을 반환한다")
    @ValueSource(strings = {"{}", "{\"refreshToken\": \" \"}"})
    void returnsBadRequestWhenRefreshTokenIsBlank(String body) throws Exception {
      // when
      ResultActions result =
          mockMvc.perform(
              post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body));

      // then
      result.andExpect(status().isBadRequest());
      then(authService).shouldHaveNoInteractions();
    }
  }

  @Nested
  @DisplayName("로그아웃")
  class Logout {

    @Test
    @DisplayName("로그아웃하면 해당 로그인 세션을 무효화하고 204를 반환한다")
    void returnsNoContentWhenLogoutSucceeds() throws Exception {
      // given
      Member member = Member.create("user@example.com", "{bcrypt}hash");
      ReflectionTestUtils.setField(member, "id", 1L);
      String accessToken =
          new AccessTokenProvider(jwtEncoder, properties, Clock.systemUTC())
              .issue(member, "family");

      // when
      ResultActions result =
          mockMvc.perform(
              post("/api/v1/auth/logout")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));

      // then
      result.andExpect(status().isNoContent());
      then(authService).should().logout("family");
    }
  }
}
