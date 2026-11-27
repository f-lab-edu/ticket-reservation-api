package com.ticketing.reservation.integration.performance;

import static com.ticketing.reservation.integration.ConcurrencyTestSupport.runWhileOtherTransactionHoldsLock;
import static org.assertj.core.api.Assertions.assertThat;

import com.ticketing.reservation.integration.TestcontainersConfiguration;
import com.ticketing.reservation.performance.domain.Performance;
import com.ticketing.reservation.performance.domain.PerformanceInfo;
import com.ticketing.reservation.performance.domain.PerformanceStock;
import com.ticketing.reservation.performance.repository.PerformanceRepository;
import com.ticketing.reservation.performance.repository.PerformanceStockRepository;
import java.time.LocalDateTime;
import java.util.Optional;
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

    // when
    Optional<PerformanceStock> stockWhileLocked =
        runWhileOtherTransactionHoldsLock(
            transactionManager,
            () ->
                assertThat(stockRepository.findByIdForUpdateSkipLocked(performance.getId()))
                    .isPresent(),
            () ->
                new TransactionTemplate(transactionManager)
                    .execute(
                        status ->
                            stockRepository.findByIdForUpdateSkipLocked(performance.getId())));

    // then
    assertThat(stockWhileLocked).isEmpty();
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
}
