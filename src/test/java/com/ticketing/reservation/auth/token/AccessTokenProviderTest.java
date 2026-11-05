package com.ticketing.reservation.auth.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.ticketing.reservation.auth.config.AuthProperties;
import com.ticketing.reservation.auth.config.JwtConfig;
import com.ticketing.reservation.member.domain.Member;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

class AccessTokenProviderTest {

  private static final String SECRET = "unit-test-jwt-secret-key-32-bytes-or-more";
  private static final Duration VALIDITY = Duration.ofMinutes(30);
  private static final Duration DEFAULT_CLOCK_SKEW = Duration.ofSeconds(60);

  private final AuthProperties properties = new AuthProperties(SECRET, VALIDITY);
  private final JwtConfig jwtConfig = new JwtConfig(properties);
  private final JwtDecoder jwtDecoder = jwtConfig.jwtDecoder();

  @Test
  @DisplayName("발급한 토큰에 회원 식별자, 권한 및 만료 정보가 포함된다")
  void issuesTokenWithMemberIdAndRole() {
    // given
    Instant now = Instant.now();
    String token = providerAt(now).issue(member(1L));

    // when
    Jwt jwt = jwtDecoder.decode(token);

    // then
    assertThat(jwt.getSubject()).isEqualTo("1");
    assertThat(jwt.getClaimAsString(AccessTokenProvider.ROLE_CLAIM)).isEqualTo("USER");
    assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
    assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plus(VALIDITY));
  }

  @Test
  @DisplayName("만료된 토큰이면 검증에 실패한다")
  void rejectsExpiredToken() {
    // given
    Instant issuedAt = Instant.now().minus(VALIDITY).minus(DEFAULT_CLOCK_SKEW).minusSeconds(1);
    String token = providerAt(issuedAt).issue(member(1L));

    // when
    Throwable thrown = catchThrowable(() -> jwtDecoder.decode(token));

    // then
    assertThat(thrown).isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("다른 키로 서명된 토큰이면 검증에 실패한다")
  void rejectsTokenSignedWithDifferentKey() {
    // given
    AuthProperties otherProperties =
        new AuthProperties("other-jwt-secret-key-also-32-bytes-or-more", VALIDITY);
    String token =
        new AccessTokenProvider(
                new JwtConfig(otherProperties).jwtEncoder(), otherProperties, Clock.systemUTC())
            .issue(member(1L));

    // when
    Throwable thrown = catchThrowable(() -> jwtDecoder.decode(token));

    // then
    assertThat(thrown).isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("변조된 토큰이면 검증에 실패한다")
  void rejectsTamperedToken() {
    // given
    String[] parts = providerAt(Instant.now()).issue(member(1L)).split("\\.");
    String forgedPayload =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                "{\"sub\":\"1\",\"role\":\"ADMIN\",\"exp\":9999999999}"
                    .getBytes(StandardCharsets.UTF_8));
    String forged = parts[0] + "." + forgedPayload + "." + parts[2];

    // when
    Throwable thrown = catchThrowable(() -> jwtDecoder.decode(forged));

    // then
    assertThat(thrown).isInstanceOf(JwtException.class);
  }

  private AccessTokenProvider providerAt(Instant now) {
    return new AccessTokenProvider(
        jwtConfig.jwtEncoder(), properties, Clock.fixed(now, ZoneOffset.UTC));
  }

  private Member member(Long id) {
    Member member = Member.create("user@example.com", "{bcrypt}hash");
    ReflectionTestUtils.setField(member, "id", id);
    return member;
  }
}
