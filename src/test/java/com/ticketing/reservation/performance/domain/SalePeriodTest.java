package com.ticketing.reservation.performance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SalePeriodTest {

  private static final LocalDateTime SALE_START_AT = LocalDateTime.of(2026, 11, 13, 12, 0);

  @ParameterizedTest
  @DisplayName("판매 종료 일시가 판매 시작 일시와 같거나 빠르면 거절한다")
  @ValueSource(longs = {0, 1})
  void rejectsSaleEndAtOrBeforeSaleStart(long minutesBeforeSaleStart) {
    // given
    LocalDateTime saleEndAt = SALE_START_AT.minusMinutes(minutesBeforeSaleStart);

    // when
    Throwable thrown = catchThrowable(() -> new SalePeriod(SALE_START_AT, saleEndAt));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_SALE_PERIOD));
  }
}
