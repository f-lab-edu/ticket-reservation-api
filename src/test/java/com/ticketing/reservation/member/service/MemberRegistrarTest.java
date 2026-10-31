package com.ticketing.reservation.member.service;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import com.ticketing.reservation.member.repository.MemberRepository;
import java.sql.SQLIntegrityConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class MemberRegistrarTest {

  @Mock private MemberRepository memberRepository;

  @InjectMocks private MemberRegistrar memberRegistrar;

  @BeforeEach
  void setUp() {
    given(memberRepository.existsByEmail(anyString())).willReturn(false);
  }

  @Test
  void 이메일_유니크_제약을_위반하면_이메일_중복_예외로_변환한다() {
    given(memberRepository.saveAndFlush(any()))
        .willThrow(constraintViolation("member.uk_member_email"));

    assertThatExceptionOfType(BusinessException.class)
        .isThrownBy(() -> memberRegistrar.register("user@example.com", "{bcrypt}hash"))
        .extracting(BusinessException::getErrorCode)
        .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = "member.uk_member_nickname")
  void 이메일_유니크_제약이_아닌_제약을_위반하면_이메일_중복으로_변환하지_않는다(String constraintName) {
    DataIntegrityViolationException violation = constraintViolation(constraintName);
    given(memberRepository.saveAndFlush(any())).willThrow(violation);

    assertThatThrownBy(() -> memberRegistrar.register("user@example.com", "{bcrypt}hash"))
        .isSameAs(violation);
  }

  // JPA 저장 시 Spring이 Hibernate ConstraintViolationException을 감싸 던지는 형태를 재현한다.
  private DataIntegrityViolationException constraintViolation(String constraintName) {
    ConstraintViolationException cause =
        new ConstraintViolationException(
            "could not execute statement",
            new SQLIntegrityConstraintViolationException(),
            constraintName);
    return new DataIntegrityViolationException("could not execute statement", cause);
  }
}
