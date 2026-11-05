package com.ticketing.reservation.auth.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("auth")
public record AuthProperties(String jwtSecret, Duration accessTokenValidity) {

  private static final int MIN_SECRET_BYTES = 32;

  public AuthProperties {
    if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
      throw new IllegalArgumentException("auth.jwt-secret은 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다.");
    }
  }

  public byte[] jwtSecretBytes() {
    return jwtSecret.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public String toString() {
    return "AuthProperties[accessTokenValidity=" + accessTokenValidity + "]";
  }
}
