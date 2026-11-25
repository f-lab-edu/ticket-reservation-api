package com.ticketing.reservation.integration.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketing.reservation.integration.TestcontainersConfiguration;
import com.ticketing.reservation.performance.domain.Performance;
import com.ticketing.reservation.performance.domain.PerformanceInfo;
import com.ticketing.reservation.performance.domain.PerformanceStock;
import com.ticketing.reservation.performance.repository.PerformanceRepository;
import com.ticketing.reservation.performance.repository.PerformanceStockRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PerformanceStockRepositoryTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 1, 12, 0);
  private static final Duration LOCK_HOLD_LIMIT = Duration.ofSeconds(10);
  private static final Duration WAIT_LIMIT = Duration.ofSeconds(10);

  @Autowired private PerformanceRepository performanceRepository;
  @Autowired private PerformanceStockRepository stockRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  @AfterEach
  void tearDown() {
    stockRepository.deleteAllInBatch();
    performanceRepository.deleteAllInBatch();
  }

  @Test
  @DisplayName("재고 락을 획득하면 다른 트랜잭션의 같은 조회는 빈 결과를 반환한다")
  void locksStockAgainstSameQuery() throws Exception {
    // given
    Performance performance = save();
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);

    try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
      // when
      Future<Optional<PerformanceStock>> lockHolder =
          executor.submit(
              () ->
                  transaction.execute(
                      status -> {
                        Optional<PerformanceStock> stock =
                            stockRepository.findByIdForUpdateSkipLocked(performance.getId());
                        locked.countDown();
                        awaitRelease(release);
                        return stock;
                      }));
      assertThat(locked.await(WAIT_LIMIT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
      Optional<PerformanceStock> other =
          transaction.execute(
              status -> stockRepository.findByIdForUpdateSkipLocked(performance.getId()));

      // then
      assertThat(other).isEmpty();
      release.countDown();
      assertThat(lockHolder.get())
          .hasValueSatisfying(s -> assertThat(s.getPerformanceId()).isEqualTo(performance.getId()));
    }
  }

  private Performance save() {
    Performance performance =
        performanceRepository.save(
            Performance.create(
                new PerformanceInfo(
                    "공연", "공연장", NOW.plusDays(30), 50_000, NOW.minusDays(1), NOW.plusDays(1)),
                NOW.minusDays(2)));
    stockRepository.save(PerformanceStock.create(performance.getId(), 100));
    return performance;
  }

  private static void awaitRelease(CountDownLatch latch) {
    try {
      if (!latch.await(LOCK_HOLD_LIMIT.toMillis(), TimeUnit.MILLISECONDS)) {
        throw new IllegalStateException("재고 락 점유 제한 시간 초과");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("재고 락 점유 스레드가 중단되었습니다", e);
    }
  }
}
