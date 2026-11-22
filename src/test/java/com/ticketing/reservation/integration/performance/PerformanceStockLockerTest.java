package com.ticketing.reservation.integration.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.ticketing.reservation.integration.TestcontainersConfiguration;
import com.ticketing.reservation.performance.service.PerformanceStockLocker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.IllegalTransactionStateException;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PerformanceStockLockerTest {

  @Autowired private PerformanceStockLocker stockLocker;

  @Test
  @DisplayName("트랜잭션 없이 재고를 잠그면 거절한다")
  void rejectsLockWithoutTransaction() {
    // when
    Throwable thrown = catchThrowable(() -> stockLocker.lock(1L));

    // then
    assertThat(thrown).isInstanceOf(IllegalTransactionStateException.class);
  }
}
