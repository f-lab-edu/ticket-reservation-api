package com.ticketing.reservation.auth.repository;

import com.ticketing.reservation.auth.config.RefreshTokenProperties;
import com.ticketing.reservation.auth.domain.RefreshToken;
import com.ticketing.reservation.auth.domain.RefreshTokenStatus;
import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class RefreshTokenRepository {

  private static final String KEY_PREFIX = "auth:refresh-token:";

  private static final String FIELD_MEMBER_ID = "memberId";
  private static final String FIELD_FAMILY_ID = "familyId";
  private static final String FIELD_STATUS = "status";
  private static final String FIELD_EXPIRES_AT = "expiresAt";
  private static final String FIELD_CREATED_AT = "createdAt";

  private static final String CURRENT_TIME_MILLIS =
      """
      local time = redis.call('TIME')
      local now = time[1] * 1000 + math.floor(time[2] / 1000)
      """;

  private static final RedisScript<Long> SAVE =
      RedisScript.of(
          CURRENT_TIME_MILLIS
              + """
              local expiresAt = now + tonumber(ARGV[3])
              redis.call('HSET', KEYS[1],
                'memberId', ARGV[1],
                'familyId', ARGV[2],
                'status', 'ACTIVE',
                'createdAt', string.format('%.0f', now),
                'expiresAt', string.format('%.0f', expiresAt))
              redis.call('PEXPIREAT', KEYS[1], string.format('%.0f', expiresAt + tonumber(ARGV[4])))
              return 1
              """,
          Long.class);

  private final StringRedisTemplate redisTemplate;
  private final long validityMillis;
  private final long retentionMillis;

  public RefreshTokenRepository(
      StringRedisTemplate redisTemplate, RefreshTokenProperties properties) {
    this.redisTemplate = redisTemplate;
    this.validityMillis = properties.validity().toMillis();
    this.retentionMillis = properties.retention().toMillis();
  }

  public void save(String tokenHash, Long memberId, String familyId) {
    execute(
        () ->
            redisTemplate.execute(
                SAVE,
                List.of(key(tokenHash)),
                String.valueOf(memberId),
                familyId,
                String.valueOf(validityMillis),
                String.valueOf(retentionMillis)));
  }

  public Optional<RefreshToken> findByTokenHash(String tokenHash) {
    Map<Object, Object> fields = execute(() -> redisTemplate.opsForHash().entries(key(tokenHash)));
    if (fields.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(toToken(tokenHash, fields));
  }

  private static RefreshToken toToken(String tokenHash, Map<Object, Object> fields) {
    return new RefreshToken(
        tokenHash,
        Long.valueOf((String) fields.get(FIELD_MEMBER_ID)),
        (String) fields.get(FIELD_FAMILY_ID),
        RefreshTokenStatus.valueOf((String) fields.get(FIELD_STATUS)),
        toInstant(fields.get(FIELD_EXPIRES_AT)),
        toInstant(fields.get(FIELD_CREATED_AT)));
  }

  private static Instant toInstant(Object epochMillis) {
    return Instant.ofEpochMilli(Long.parseLong((String) epochMillis));
  }

  private static String key(String tokenHash) {
    return KEY_PREFIX + tokenHash;
  }

  private static <T> T execute(Supplier<T> command) {
    try {
      return command.get();
    } catch (DataAccessException e) {
      throw new BusinessException(ErrorCode.AUTH_STORE_UNAVAILABLE, e);
    }
  }
}
