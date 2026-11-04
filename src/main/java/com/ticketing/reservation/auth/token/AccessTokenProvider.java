package com.ticketing.reservation.auth.token;

import com.ticketing.reservation.auth.config.AuthProperties;
import com.ticketing.reservation.member.domain.Member;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
public class AccessTokenProvider {

  public static final String ROLE_CLAIM = "role";

  private final JwtEncoder jwtEncoder;
  private final Duration validity;
  private final Clock clock;

  public AccessTokenProvider(JwtEncoder jwtEncoder, AuthProperties properties, Clock clock) {
    this.jwtEncoder = jwtEncoder;
    this.validity = properties.accessTokenValidity();
    this.clock = clock;
  }

  public String issue(Member member) {
    Instant now = clock.instant();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(String.valueOf(member.getId()))
            .claim(ROLE_CLAIM, member.getRole().name())
            .issuedAt(now)
            .expiresAt(now.plus(validity))
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  public long validitySeconds() {
    return validity.toSeconds();
  }
}
