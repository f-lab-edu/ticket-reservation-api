package com.ticketing.reservation.member.email;

import java.util.Locale;

public final class EmailNormalizer {

  private EmailNormalizer() {}

  public static String normalize(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }
}
