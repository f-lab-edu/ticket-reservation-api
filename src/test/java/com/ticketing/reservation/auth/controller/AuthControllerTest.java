package com.ticketing.reservation.auth.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketing.reservation.auth.config.JwtConfig;
import com.ticketing.reservation.auth.dto.TokenResponse;
import com.ticketing.reservation.auth.service.AuthService;
import com.ticketing.reservation.global.config.SecurityConfig;
import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class AuthControllerTest {

  private static final TokenResponse TOKENS = TokenResponse.bearer("access-token", 1800);

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuthService authService;

  @Test
  @DisplayName("로그인에 성공하면 200과 토큰을 반환한다")
  void returnsTokensWhenLoginSucceeds() throws Exception {
    given(authService.login("user@example.com", "password")).willReturn(TOKENS);

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "user@example.com", "password": "password"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.accessToken").value("access-token"))
        .andExpect(jsonPath("$.expiresIn").value(1800));
  }

  @Test
  @DisplayName("로그인에 실패하면 401과 오류 코드를 반환한다")
  void returnsUnauthorizedWhenLoginFails() throws Exception {
    given(authService.login("user@example.com", "wrong"))
        .willThrow(new BusinessException(ErrorCode.INVALID_CREDENTIALS));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "user@example.com", "password": "wrong"}
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  @DisplayName("만료된 액세스 토큰을 헤더에 붙여도 로그인을 처리한다")
  void ignoresAuthorizationHeaderOnLogin() throws Exception {
    given(authService.login("user@example.com", "password")).willReturn(TOKENS);

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .header(HttpHeaders.AUTHORIZATION, "Bearer expired.or.invalid")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "user@example.com", "password": "password"}
                    """))
        .andExpect(status().isOk());
  }

  @ParameterizedTest
  @DisplayName("로그인 요청값이 유효하지 않으면 400을 반환한다")
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
    mockMvc
        .perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(authService);
  }
}
