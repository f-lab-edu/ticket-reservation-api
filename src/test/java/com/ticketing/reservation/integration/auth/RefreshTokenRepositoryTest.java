package com.ticketing.reservation.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketing.reservation.auth.config.RefreshTokenProperties;
import com.ticketing.reservation.auth.domain.RefreshToken;
import com.ticketing.reservation.auth.domain.RefreshTokenStatus;
import com.ticketing.reservation.auth.repository.RefreshTokenRepository;
import com.ticketing.reservation.auth.repository.RotationResult;
import com.ticketing.reservation.integration.TestcontainersConfiguration;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.testcontainers.containers.GenericContainer;

class RefreshTokenRepositoryTest {

  private static final Duration VALIDITY = Duration.ofDays(7);
  private static final Duration RETENTION = Duration.ofDays(3);
  private static final String FAMILY = "family";
  private static final Long MEMBER_ID = 1L;
  private static final String KEY_PREFIX = "auth:refresh-token:";
  private static final String FAMILY_KEY_PREFIX = "auth:refresh-token-family:";

  private static final RedisScript<Long> PEXPIRETIME =
      RedisScript.of("return redis.call('PEXPIRETIME', KEYS[1])", Long.class);

  private static final GenericContainer<?> REDIS = TestcontainersConfiguration.newRedisContainer();

  private static LettuceConnectionFactory connectionFactory;
  private static StringRedisTemplate redisTemplate;

  private RefreshTokenRepository repository;

  @BeforeAll
  static void startRedis() {
    REDIS.start();
    connectionFactory =
        new LettuceConnectionFactory(
            REDIS.getHost(), REDIS.getMappedPort(TestcontainersConfiguration.REDIS_PORT));
    connectionFactory.afterPropertiesSet();
    redisTemplate = new StringRedisTemplate(connectionFactory);
  }

  @AfterAll
  static void stopRedis() {
    connectionFactory.destroy();
    REDIS.stop();
  }

  @BeforeEach
  void setUp() {
    try (RedisConnection connection = connectionFactory.getConnection()) {
      connection.serverCommands().flushAll();
    }
    repository =
        new RefreshTokenRepository(redisTemplate, new RefreshTokenProperties(VALIDITY, RETENTION));
  }

  @Test
  @DisplayName("리프레시 토큰을 저장하면 만료 시각에 보존 기간을 더한 시점에 삭제되도록 설정한다")
  void setsKeyExpiryToExpiresAtPlusRetention() {
    // when
    repository.save("hash", MEMBER_ID, FAMILY);

    // then
    RefreshToken token = repository.findByTokenHash("hash").orElseThrow();
    assertThat(expireTimeMillis("hash"))
        .isEqualTo(token.expiresAt().plus(RETENTION).toEpochMilli());
  }

  @Test
  @DisplayName("재발급 시 ACTIVE 토큰이면 USED로 변경하고 같은 회원과 로그인 세션의 새 토큰을 저장한다")
  void rotatesActiveToken() {
    // given
    repository.save("old", MEMBER_ID, FAMILY);

    // when
    RotationResult result = repository.rotate("old", "new", FAMILY);

    // then
    RefreshToken usedToken = repository.findByTokenHash("old").orElseThrow();
    RefreshToken newToken = repository.findByTokenHash("new").orElseThrow();

    assertThat(result).isEqualTo(RotationResult.ROTATED);
    assertThat(usedToken.status()).isEqualTo(RefreshTokenStatus.USED);

    assertThat(newToken.memberId()).isEqualTo(MEMBER_ID);
    assertThat(newToken.familyId()).isEqualTo(FAMILY);
    assertThat(newToken.status()).isEqualTo(RefreshTokenStatus.ACTIVE);
    assertThat(newToken.expiresAt()).isEqualTo(newToken.createdAt().plus(VALIDITY));
  }

  @Test
  @DisplayName("재발급 시 USED 토큰이면 재사용으로 판단하고 해당 로그인 세션을 무효화한다")
  void revokesFamilyWhenUsedTokenIsRotated() {
    // given
    repository.save("old", MEMBER_ID, FAMILY);
    repository.rotate("old", "new", FAMILY);

    // when
    RotationResult reused = repository.rotate("old", "reused", FAMILY);
    RotationResult afterRevoke = repository.rotate("new", "revoked", FAMILY);

    // then
    assertThat(reused).isEqualTo(RotationResult.REUSED);
    assertThat(afterRevoke).isEqualTo(RotationResult.REJECTED);
    assertThat(repository.findByTokenHash("reused")).isEmpty();
    assertThat(repository.findByTokenHash("revoked")).isEmpty();
    assertThat(redisTemplate.getExpire(FAMILY_KEY_PREFIX + FAMILY, TimeUnit.MILLISECONDS))
        .isBetween(VALIDITY.minusMinutes(1).toMillis(), VALIDITY.toMillis());
  }

  @Test
  @DisplayName("재발급 시 만료된 USED 토큰이어도 재사용으로 판단하고 해당 로그인 세션을 무효화한다")
  void revokesFamilyWhenExpiredUsedTokenIsRotated() {
    // given
    repository.save("old", MEMBER_ID, FAMILY);
    repository.rotate("old", "new", FAMILY);
    redisTemplate.opsForHash().put(KEY_PREFIX + "old", "expiresAt", "1");

    // when
    RotationResult reused = repository.rotate("old", "reused", FAMILY);
    RotationResult afterRevoke = repository.rotate("new", "revoked", FAMILY);

    // then
    assertThat(reused).isEqualTo(RotationResult.REUSED);
    assertThat(afterRevoke).isEqualTo(RotationResult.REJECTED);
  }

  @Test
  @DisplayName("로그인 세션을 무효화하면 무효화 여부가 조회되고 해당 세션의 토큰은 교체할 수 없다")
  void revokesFamily() {
    // given
    repository.save("token", MEMBER_ID, FAMILY);
    boolean revokedBefore = repository.isFamilyRevoked(FAMILY);

    // when
    repository.revokeFamily(FAMILY);

    // then
    assertThat(revokedBefore).isFalse();
    assertThat(repository.isFamilyRevoked(FAMILY)).isTrue();
    assertThat(repository.isFamilyRevoked("other-family")).isFalse();
    assertThat(repository.rotate("token", "new", FAMILY)).isEqualTo(RotationResult.REJECTED);
    assertThat(repository.findByTokenHash("new")).isEmpty();
    assertThat(redisTemplate.getExpire(FAMILY_KEY_PREFIX + FAMILY, TimeUnit.MILLISECONDS))
        .isBetween(VALIDITY.minusMinutes(1).toMillis(), VALIDITY.toMillis());
  }

  private static long expireTimeMillis(String tokenHash) {
    return redisTemplate.execute(PEXPIRETIME, List.of(KEY_PREFIX + tokenHash));
  }
}
