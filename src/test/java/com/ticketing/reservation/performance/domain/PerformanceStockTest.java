package com.ticketing.reservation.performance.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PerformanceStockTest {

  @Test
  @DisplayName("재고를 생성하면 잔여 판매 수량이 총 판매 수량과 같다")
  void setsRemainingQuantityToTotalQuantity() {
    // when
    PerformanceStock stock = PerformanceStock.create(1L, 100);

    // then
    assertThat(stock.getTotalQuantity()).isEqualTo(100);
    assertThat(stock.getRemainingQuantity()).isEqualTo(100);
  }
}
