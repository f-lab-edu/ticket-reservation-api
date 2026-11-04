package com.ticketing.reservation.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthPropertiesTest {

  private static final String SECRET = "unit-test-jwt-secret-key-32-bytes-or-more";
  private static final Duration VALIDITY = Duration.ofMinutes(30);

  @Test
  @DisplayName("32바이트보다 짧은 서명 키는 허용하지 않는다")
  void rejectsShortSecret() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new AuthProperties("a".repeat(31), VALIDITY));
  }

  @Test
  @DisplayName("설정값의 문자열 표현에 서명 키를 노출하지 않는다")
  void hidesSecretInToString() {
    AuthProperties properties = new AuthProperties(SECRET, VALIDITY);

    assertThat(properties.toString()).doesNotContain(SECRET);
  }
}
