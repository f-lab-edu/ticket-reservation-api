package com.ticketing.reservation.performance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    PerformanceStock stock = stock(100, 30);

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
    PerformanceStock stock = stock(100, 30);

    // when
    Throwable thrown = catchThrowable(() -> stock.changeTotalQuantity(29));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOTAL_QUANTITY_BELOW_RESERVED);
              assertThat(e.getProperties()).containsEntry("reservedQuantity", 30);
            });
  }

  @Test
  @DisplayName("예약하면 예약 수량이 늘어난다")
  void reservesQuantity() {
    // given
    PerformanceStock stock = stock(100, 30);

    // when
    stock.reserve(5);

    // then
    assertThat(stock.getReservedQuantity()).isEqualTo(35);
  }

  @Test
  @DisplayName("잔여 수량과 같은 수량은 예약할 수 있다")
  void reservesAllRemainingQuantity() {
    // given
    PerformanceStock stock = stock(100, 30);

    // when
    stock.reserve(70);

    // then
    assertThat(stock.getReservedQuantity()).isEqualTo(100);
  }

  @Test
  @DisplayName("잔여 수량보다 많이 예약하면 거절하고 예약 수량을 바꾸지 않는다")
  void rejectsReservationOverRemaining() {
    // given
    PerformanceStock stock = stock(100, 30);

    // when
    Throwable thrown = catchThrowable(() -> stock.reserve(71));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
              assertThat(e.getProperties()).containsEntry("remainingQuantity", 70);
            });
    assertThat(stock.getReservedQuantity()).isEqualTo(30);
  }

  @Test
  @DisplayName("재고를 복구하면 예약 수량이 줄어든다")
  void releasesQuantity() {
    // given
    PerformanceStock stock = stock(100, 30);

    // when
    stock.release(30);

    // then
    assertThat(stock.getReservedQuantity()).isZero();
    assertThat(stock.hasReservation()).isFalse();
  }

  @ParameterizedTest(name = "{0}개")
  @DisplayName("0 미만이거나 예약 수량보다 많은 수량은 복구할 수 없다")
  @ValueSource(ints = {-1, 31})
  void rejectsReleaseOutOfRange(int quantity) {
    // given
    PerformanceStock stock = stock(100, 30);

    // when
    Throwable thrown = catchThrowable(() -> stock.release(quantity));

    // then
    assertThat(thrown).isInstanceOf(IllegalStateException.class);
    assertThat(stock.getReservedQuantity()).isEqualTo(30);
  }

  private PerformanceStock stock(int totalQuantity, int reservedQuantity) {
    PerformanceStock stock = PerformanceStock.create(1L, totalQuantity);
    ReflectionTestUtils.setField(stock, "reservedQuantity", reservedQuantity);
    return stock;
  }
}
