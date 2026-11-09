package com.ticketing.reservation.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("auth.refresh-token")
public record RefreshTokenProperties(Duration validity, Duration retention) {

  public RefreshTokenProperties {
    if (validity == null || validity.isNegative() || validity.isZero()) {
      throw new IllegalArgumentException("auth.refresh-token.validity는 0보다 커야 합니다.");
    }
    if (retention == null || retention.isNegative()) {
      throw new IllegalArgumentException("auth.refresh-token.retention은 0 이상이어야 합니다.");
    }
  }
}
