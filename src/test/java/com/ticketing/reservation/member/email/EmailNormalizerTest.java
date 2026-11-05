package com.ticketing.reservation.member.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailNormalizerTest {

  @ParameterizedTest
  @DisplayName("정규화하면 앞뒤 공백을 제거하고 소문자로 변환한다")
  @ValueSource(strings = {"user@example.com", "USER@EXAMPLE.COM", "  User@Example.Com\t"})
  void trimsAndLowercases(String email) {
    // when
    String result = EmailNormalizer.normalize(email);

    // then
    assertThat(result).isEqualTo("user@example.com");
  }

  @Test
  @DisplayName("서버 기본 로케일이 달라도 동일한 정규화 결과를 반환한다")
  void lowercasesRegardlessOfDefaultLocale() {
    // given
    Locale original = Locale.getDefault();
    Locale.setDefault(Locale.forLanguageTag("tr-TR"));
    try {
      // when
      String result = EmailNormalizer.normalize("ADMIN@EXAMPLE.COM");

      // then
      assertThat(result).isEqualTo("admin@example.com");
    } finally {
      Locale.setDefault(original);
    }
  }
}
