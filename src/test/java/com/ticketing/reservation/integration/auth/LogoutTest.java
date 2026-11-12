package com.ticketing.reservation.integration.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketing.reservation.auth.dto.TokenResponse;
import com.ticketing.reservation.auth.service.AuthService;
import com.ticketing.reservation.integration.TestcontainersConfiguration;
import com.ticketing.reservation.member.repository.MemberRepository;
import com.ticketing.reservation.member.service.MemberService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LogoutTest {

  private static final String EMAIL = "logout@example.com";
  private static final String PASSWORD = "password";

  // 없는 경로라서 인증을 통과하면 404, 인증에 실패하면 401이 된다.
  private static final String PROTECTED_PATH = "/api/v1/protected";

  @Autowired private MockMvc mockMvc;
  @Autowired private AuthService authService;
  @Autowired private MemberService memberService;
  @Autowired private MemberRepository memberRepository;

  @BeforeEach
  void setUp() {
    memberService.signUp(EMAIL, PASSWORD);
  }

  @AfterEach
  void tearDown() {
    memberRepository.deleteAllInBatch();
  }

  @Test
  @DisplayName("로그아웃하면 해당 로그인 세션의 토큰만 즉시 거부한다")
  void revokesOnlyLoggedOutSession() throws Exception {
    // given
    TokenResponse login = authService.login(EMAIL, PASSWORD);
    TokenResponse refreshed = authService.refresh(login.refreshToken());
    TokenResponse otherLogin = authService.login(EMAIL, PASSWORD);

    // when
    ResultActions logout = requestLogout(refreshed.accessToken());

    // then
    logout.andExpect(status().isNoContent());
    requestLogout(refreshed.accessToken()).andExpect(status().isUnauthorized());
    requestProtected(login.accessToken())
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    requestProtected(refreshed.accessToken())
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    requestProtected(otherLogin.accessToken()).andExpect(status().isNotFound());
  }

  private ResultActions requestLogout(String accessToken) throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
  }

  private ResultActions requestProtected(String accessToken) throws Exception {
    return mockMvc.perform(
        get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
  }
}
