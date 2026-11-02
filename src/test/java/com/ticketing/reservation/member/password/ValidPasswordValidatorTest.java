package com.ticketing.reservation.member.password;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ValidPasswordValidatorTest {

  private final ValidPasswordValidator validator = new ValidPasswordValidator();

  @Test
  @DisplayName("72바이트 비밀번호는 통과한다")
  void acceptsPasswordOf72Bytes() {
    assertThat(validator.isValid("a".repeat(72), null)).isTrue();
  }

  @Test
  @DisplayName("73바이트 비밀번호는 실패한다")
  void rejectsPasswordOver72Bytes() {
    assertThat(validator.isValid("a".repeat(73), null)).isFalse();
  }

  @Test
  @DisplayName("문자 수가 아닌 UTF-8 바이트 수로 길이를 비교한다")
  void comparesUtf8ByteLength() {
    assertThat(validator.isValid("가".repeat(24), null)).isTrue();
    assertThat(validator.isValid("가".repeat(25), null)).isFalse();
  }

  // 필수 여부는 @NotBlank가 검증한다.
  @Test
  @DisplayName("null은 통과시킨다")
  void acceptsNullPassword() {
    assertThat(validator.isValid(null, null)).isTrue();
  }
}
