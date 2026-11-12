package com.ticketing.reservation.performance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PerformanceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 12, 0);
  private static final LocalDateTime SALE_START_AT = NOW.plusDays(1);
  private static final LocalDateTime SALE_END_AT = NOW.plusDays(10);
  private static final LocalDateTime PERFORMANCE_AT = NOW.plusDays(20);

  @Test
  @DisplayName("공연을 생성하면 잔여 판매 수량이 총 판매 수량과 같다")
  void setsRemainingQuantityToTotalQuantity() {
    // when
    Performance performance = create(PERFORMANCE_AT, SALE_START_AT, SALE_END_AT);

    // then
    assertThat(performance.getTotalQuantity()).isEqualTo(100);
    assertThat(performance.getRemainingQuantity()).isEqualTo(100);
  }

  @ParameterizedTest
  @DisplayName("판매 시작 일시가 현재 시각과 같거나 빠르면 거절한다")
  @ValueSource(longs = {0, 1})
  void rejectsSaleStartAtOrBeforeNow(long minutesBeforeNow) {
    // given
    LocalDateTime saleStartAt = NOW.minusMinutes(minutesBeforeNow);

    // when
    Throwable thrown = catchThrowable(() -> create(PERFORMANCE_AT, saleStartAt, SALE_END_AT));

    // then
    assertErrorCode(thrown, ErrorCode.INVALID_SALE_START_AT);
  }

  @ParameterizedTest
  @DisplayName("판매 종료 일시가 판매 시작 일시와 같거나 빠르면 거절한다")
  @ValueSource(longs = {0, 1})
  void rejectsSaleEndAtOrBeforeSaleStart(long minutesBeforeSaleStart) {
    // given
    LocalDateTime saleEndAt = SALE_START_AT.minusMinutes(minutesBeforeSaleStart);

    // when
    Throwable thrown = catchThrowable(() -> create(PERFORMANCE_AT, SALE_START_AT, saleEndAt));

    // then
    assertErrorCode(thrown, ErrorCode.INVALID_SALE_PERIOD);
  }

  @ParameterizedTest
  @DisplayName("판매 종료 일시가 공연 일시와 같거나 늦으면 거절한다")
  @ValueSource(longs = {0, 1})
  void rejectsSaleEndAtOrAfterPerformance(long minutesAfterPerformance) {
    // given
    LocalDateTime saleEndAt = PERFORMANCE_AT.plusMinutes(minutesAfterPerformance);

    // when
    Throwable thrown = catchThrowable(() -> create(PERFORMANCE_AT, SALE_START_AT, saleEndAt));

    // then
    assertErrorCode(thrown, ErrorCode.SALE_END_AFTER_PERFORMANCE);
  }

  private Performance create(
      LocalDateTime performanceAt, LocalDateTime saleStartAt, LocalDateTime saleEndAt) {
    return Performance.create("공연", "공연장", performanceAt, 50_000, 100, saleStartAt, saleEndAt, NOW);
  }

  private void assertErrorCode(Throwable thrown, ErrorCode errorCode) {
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
  }
}
