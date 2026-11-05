package com.ticketing.reservation.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthPropertiesTest {

  private static final String SECRET = "unit-test-jwt-secret-key-32-bytes-or-more";
  private static final Duration VALIDITY = Duration.ofMinutes(30);

  @Test
  @DisplayName("서명 키가 32바이트 미만이면 예외가 발생한다")
  void rejectsShortSecret() {
    // given
    String shortSecret = "a".repeat(31);

    // when
    Throwable thrown = catchThrowable(() -> new AuthProperties(shortSecret, VALIDITY));

    // then
    assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("문자열 변환 시 서명 키를 노출하지 않는다")
  void hidesSecretInToString() {
    // given
    AuthProperties properties = new AuthProperties(SECRET, VALIDITY);

    // when
    String result = properties.toString();

    // then
    assertThat(result).doesNotContain(SECRET);
  }
}
