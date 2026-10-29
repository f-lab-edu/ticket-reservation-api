package com.ticketing.reservation.member.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailNormalizerTest {

  @ParameterizedTest
  @ValueSource(strings = {"user@example.com", "USER@EXAMPLE.COM", "  User@Example.Com\t"})
  void 앞뒤_공백을_제거하고_소문자로_변환한다(String email) {
    assertThat(EmailNormalizer.normalize(email)).isEqualTo("user@example.com");
  }

  @Test
  void 서버_기본_로케일과_관계없이_소문자로_변환한다() {
    Locale original = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));

      assertThat(EmailNormalizer.normalize("ADMIN@EXAMPLE.COM")).isEqualTo("admin@example.com");
    } finally {
      Locale.setDefault(original);
    }
  }
}
