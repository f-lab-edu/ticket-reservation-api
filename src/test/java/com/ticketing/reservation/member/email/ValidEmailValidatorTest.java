package com.ticketing.reservation.member.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ValidEmailValidatorTest {

  private final ValidEmailValidator validator = new ValidEmailValidator();

  @ParameterizedTest
  @ValueSource(
      strings = {
        "user@example.com",
        "User.Name@Example.COM",
        "a.b.c@sub.example.co.kr",
        "user@my-domain.com",
        "123@456.789",
        "!#$%&'*+-/=?^_`{|}~@example.com",
        "  user@example.com  ",
        "\tuser@example.com\n"
      })
  void 형식이_올바른_이메일은_통과한다(String email) {
    assertThat(validator.isValid(email, null)).isTrue();
  }

  // 필수 여부는 @NotBlank가 검증한다.
  @Test
  void null은_통과시킨다() {
    assertThat(validator.isValid(null, null)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "   ",
        "userexample.com",
        "@example.com",
        "user@",
        "user@@example.com",
        "us@er@example.com",
        // 4.1.1.1 로컬 파트 허용 문자
        "us er@example.com",
        "\"user\"@example.com",
        "user(comment)@example.com",
        "사용자@example.com",
        // 4.1.1.2 마침표 위치
        ".user@example.com",
        "user.@example.com",
        "us..er@example.com",
        "user@.example.com",
        "user@example.com.",
        "user@example..com",
        // 4.1.1.3 도메인 허용 문자
        "user@exam_ple.com",
        "user@[127.0.0.1]",
        "user@예시.com",
        // 4.1.1.4 레이블 구성
        "user@localhost",
        "user@-example.com",
        "user@example-.com",
        "user@example.-com"
      })
  void 형식이_올바르지_않은_이메일은_실패한다(String email) {
    assertThat(validator.isValid(email, null)).isFalse();
  }

  @ParameterizedTest
  @MethodSource("lengthBoundaries")
  void 길이_제한의_경계값을_검증한다(String email, boolean expected) {
    assertThat(validator.isValid(email, null)).isEqualTo(expected);
  }

  static Stream<Arguments> lengthBoundaries() {
    return Stream.of(
        arguments("a".repeat(64) + "@example.com", true),
        arguments("a".repeat(65) + "@example.com", false),
        arguments("user@" + "a".repeat(63) + ".com", true),
        arguments("user@" + "a".repeat(64) + ".com", false),
        arguments(emailOfLength(254), true),
        arguments(emailOfLength(255), false),
        arguments(" " + emailOfLength(254) + " ", true));
  }

  private static String emailOfLength(int length) {
    String localPart = "a".repeat(64);
    String domainPrefix = "b".repeat(63) + "." + "c".repeat(63) + ".";
    int lastLabelLength = length - localPart.length() - 1 - domainPrefix.length();
    return localPart + "@" + domainPrefix + "d".repeat(lastLabelLength);
  }
}
