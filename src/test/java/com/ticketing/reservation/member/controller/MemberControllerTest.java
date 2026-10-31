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
  void 사용_가능한_이메일이면_available_true를_반환한다() throws Exception {
    given(memberService.isEmailAvailable("user@example.com")).willReturn(true);

    mockMvc
        .perform(get("/api/v1/members/email-availability").param("email", "user@example.com"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(true));
  }

  @Test
  void 이미_사용_중인_이메일이면_available_false를_반환한다() throws Exception {
    given(memberService.isEmailAvailable("user@example.com")).willReturn(false);

    mockMvc
        .perform(get("/api/v1/members/email-availability").param("email", "user@example.com"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(false));
  }

  @ParameterizedTest
  @ValueSource(strings = {" ", "user@localhost"})
  void 중복_확인에서_이메일이_유효하지_않으면_400을_반환한다(String email) throws Exception {
    mockMvc
        .perform(get("/api/v1/members/email-availability").param("email", email))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(memberService);
  }

  @Test
  void 중복_확인에서_이메일_파라미터가_없으면_400을_반환한다() throws Exception {
    mockMvc.perform(get("/api/v1/members/email-availability")).andExpect(status().isBadRequest());

    verifyNoInteractions(memberService);
  }

  @Test
  void 회원_가입에_성공하면_201과_회원_정보를_반환한다() throws Exception {
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
  @ValueSource(
      strings = {
        """
        {"email": " ", "password": ""}
        """,
        """
        {"email": "user..name@example.com", "password": "password"}
        """
      })
  void 회원_가입에서_요청값이_유효하지_않으면_400을_반환한다(String body) throws Exception {
    mockMvc
        .perform(post("/api/v1/members").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(memberService);
  }

  @Test
  void 이미_사용_중인_이메일로_가입하면_409와_오류_코드를_반환한다() throws Exception {
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
