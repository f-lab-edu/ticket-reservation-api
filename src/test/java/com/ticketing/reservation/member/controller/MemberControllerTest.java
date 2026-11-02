package com.ticketing.reservation.member.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import com.ticketing.reservation.member.domain.Member;
import com.ticketing.reservation.member.service.MemberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MemberController.class)
class MemberControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MemberService memberService;

  @Test
  @DisplayName("사용 가능한 이메일이면 available true를 반환한다")
  void returnsAvailableWhenEmailIsAvailable() throws Exception {
    given(memberService.isEmailAvailable("user@example.com")).willReturn(true);

    mockMvc
        .perform(get("/api/v1/members/email-availability").param("email", "user@example.com"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(true));
  }

  @Test
  @DisplayName("이미 사용 중인 이메일이면 available false를 반환한다")
  void returnsUnavailableWhenEmailIsAlreadyInUse() throws Exception {
    given(memberService.isEmailAvailable("user@example.com")).willReturn(false);

    mockMvc
        .perform(get("/api/v1/members/email-availability").param("email", "user@example.com"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(false));
  }

  @ParameterizedTest
  @DisplayName("중복 확인에서 이메일이 유효하지 않으면 400을 반환한다")
  @ValueSource(strings = {" ", "user@localhost"})
  void returnsBadRequestWhenEmailIsInvalid(String email) throws Exception {
    mockMvc
        .perform(get("/api/v1/members/email-availability").param("email", email))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(memberService);
  }

  @Test
  @DisplayName("중복 확인에서 이메일 파라미터가 없으면 400을 반환한다")
  void returnsBadRequestWhenEmailParameterIsMissing() throws Exception {
    mockMvc.perform(get("/api/v1/members/email-availability")).andExpect(status().isBadRequest());

    verifyNoInteractions(memberService);
  }

  @Test
  @DisplayName("회원 가입에 성공하면 201과 회원 정보를 반환한다")
  void returnsCreatedWithMemberInfoWhenSignUpSucceeds() throws Exception {
    Member member = Member.create("user@example.com", "{bcrypt}hash");
    ReflectionTestUtils.setField(member, "id", 1L);
    given(memberService.signUp(anyString(), anyString())).willReturn(member);

    mockMvc
        .perform(
            post("/api/v1/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "User@Example.com", "password": "password"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.email").value("user@example.com"))
        .andExpect(jsonPath("$.role").value("USER"))
        .andExpect(jsonPath("$.password").doesNotExist());
  }

  @ParameterizedTest
  @DisplayName("회원 가입에서 요청값이 유효하지 않으면 400을 반환한다")
  @ValueSource(
      strings = {
        """
        {"email": " ", "password": ""}
        """,
        """
        {"email": "user..name@example.com", "password": "password"}
        """
      })
  void returnsBadRequestWhenSignUpRequestIsInvalid(String body) throws Exception {
    mockMvc
        .perform(post("/api/v1/members").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(memberService);
  }

  @Test
  @DisplayName("이미 사용 중인 이메일로 가입하면 409와 오류 코드를 반환한다")
  void returnsConflictWhenEmailIsAlreadyInUse() throws Exception {
    given(memberService.signUp(any(), any()))
        .willThrow(new BusinessException(ErrorCode.DUPLICATE_EMAIL));

    mockMvc
        .perform(
            post("/api/v1/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "user@example.com", "password": "password"}
                    """))
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
  }
}
