package com.ticketing.reservation.performance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PerformanceStockTest {

  @Test
  @DisplayName("재고를 생성하면 예약이 없고 잔여 수량이 총 판매 수량과 같다")
  void startsWithoutReservation() {
    // when
    PerformanceStock stock = PerformanceStock.create(1L, 100);

    // then
    assertThat(stock.getTotalQuantity()).isEqualTo(100);
    assertThat(stock.getRemainingQuantity()).isEqualTo(100);
    assertThat(stock.hasReservation()).isFalse();
  }

  @Test
  @DisplayName("총 판매 수량을 예약 수량과 같게 줄이면 잔여 수량이 0이 된다")
  void allowsTotalQuantityEqualToReserved() {
    // given
    PerformanceStock stock = stockWithReserved(30);

    // when
    stock.changeTotalQuantity(30);

    // then
    assertThat(stock.getTotalQuantity()).isEqualTo(30);
    assertThat(stock.getRemainingQuantity()).isZero();
    assertThat(stock.hasReservation()).isTrue();
  }

  @Test
  @DisplayName("총 판매 수량이 예약 수량보다 적으면 거절한다")
  void rejectsTotalQuantityBelowReserved() {
    // given
    PerformanceStock stock = stockWithReserved(30);

    // when
    Throwable thrown = catchThrowable(() -> stock.changeTotalQuantity(29));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOTAL_QUANTITY_BELOW_RESERVED));
  }

  private PerformanceStock stockWithReserved(int reservedQuantity) {
    PerformanceStock stock = PerformanceStock.create(1L, 100);
    ReflectionTestUtils.setField(stock, "reservedQuantity", reservedQuantity);
    return stock;
  }
}
