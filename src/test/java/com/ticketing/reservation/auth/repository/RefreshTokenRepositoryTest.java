package com.ticketing.reservation.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketing.reservation.TestcontainersConfiguration;
import com.ticketing.reservation.auth.config.RefreshTokenProperties;
import com.ticketing.reservation.auth.domain.RefreshToken;
import java.time.Duration;
import java.util.List;
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
  @DisplayName("저장하면 만료 시각에 보존 기간을 더한 시점에 삭제되도록 설정한다")
  void setsKeyExpiryToExpiresAtPlusRetention() {
    // when
    repository.save("hash", MEMBER_ID, FAMILY);

    // then
    RefreshToken token = repository.findByTokenHash("hash").orElseThrow();
    assertThat(expireTimeMillis()).isEqualTo(token.expiresAt().plus(RETENTION).toEpochMilli());
  }

  private static long expireTimeMillis() {
    return redisTemplate.execute(PEXPIRETIME, List.of(KEY_PREFIX + "hash"));
  }
}
