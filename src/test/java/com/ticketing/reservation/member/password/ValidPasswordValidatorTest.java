package com.ticketing.reservation.member.password;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ValidPasswordValidatorTest {

  private final ValidPasswordValidator validator = new ValidPasswordValidator();

  @Test
  @DisplayName("72바이트 비밀번호이면 통과한다")
  void acceptsPasswordOf72Bytes() {
    // given
    String password = "a".repeat(72);

    // when
    boolean result = validator.isValid(password, null);

    // then
    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("73바이트 비밀번호이면 실패한다")
  void rejectsPasswordOver72Bytes() {
    // given
    String password = "a".repeat(73);

    // when
    boolean result = validator.isValid(password, null);

    // then
    assertThat(result).isFalse();
  }

  @Test
  @DisplayName("한글 비밀번호이면 문자 수가 아닌 UTF-8 바이트 수로 비교한다")
  void comparesUtf8ByteLength() {
    // given
    String password72Bytes = "가".repeat(24);
    String password75Bytes = "가".repeat(25);

    // when
    boolean result72Bytes = validator.isValid(password72Bytes, null);
    boolean result75Bytes = validator.isValid(password75Bytes, null);

    // then
    assertThat(result72Bytes).isTrue();
    assertThat(result75Bytes).isFalse();
  }

  // 필수 여부는 @NotBlank가 검증한다.
  @Test
  @DisplayName("null이면 통과한다")
  void acceptsNullPassword() {
    // when
    boolean result = validator.isValid(null, null);

    // then
    assertThat(result).isTrue();
  }
}
