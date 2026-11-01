package com.ticketing.reservation.member.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailNormalizerTest {

  @ParameterizedTest
  @DisplayName("앞뒤 공백을 제거하고 소문자로 변환한다")
  @ValueSource(strings = {"user@example.com", "USER@EXAMPLE.COM", "  User@Example.Com\t"})
  void trimsAndLowercases(String email) {
    assertThat(EmailNormalizer.normalize(email)).isEqualTo("user@example.com");
  }

  @Test
  @DisplayName("서버 기본 로케일과 관계없이 소문자로 변환한다")
  void lowercasesRegardlessOfDefaultLocale() {
    Locale original = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));

      assertThat(EmailNormalizer.normalize("ADMIN@EXAMPLE.COM")).isEqualTo("admin@example.com");
    } finally {
      Locale.setDefault(original);
    }
  }
}
