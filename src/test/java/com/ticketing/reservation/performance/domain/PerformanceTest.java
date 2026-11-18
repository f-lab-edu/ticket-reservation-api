package com.ticketing.reservation.performance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PerformanceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 12, 0);
  private static final LocalDateTime SALE_START_AT = NOW.plusDays(1);
  private static final LocalDateTime SALE_END_AT = NOW.plusDays(10);
  private static final LocalDateTime PERFORMANCE_AT = NOW.plusDays(20);

  @ParameterizedTest
  @DisplayName("판매 시작 일시가 현재 시각과 같거나 빠르면 거절한다")
  @ValueSource(longs = {0, 1})
  void rejectsSaleStartAtOrBeforeNow(long minutesBeforeNow) {
    // given
    LocalDateTime saleStartAt = NOW.minusMinutes(minutesBeforeNow);

    // when
    Throwable thrown = catchThrowable(() -> create(saleStartAt, SALE_END_AT));

    // then
    assertErrorCode(thrown, ErrorCode.INVALID_SALE_START_AT);
  }

  @ParameterizedTest
  @DisplayName("판매 종료 일시가 공연 일시와 같거나 늦으면 거절한다")
  @ValueSource(longs = {0, 1})
  void rejectsSaleEndAtOrAfterPerformance(long minutesAfterPerformance) {
    // given
    LocalDateTime saleEndAt = PERFORMANCE_AT.plusMinutes(minutesAfterPerformance);

    // when
    Throwable thrown = catchThrowable(() -> create(SALE_START_AT, saleEndAt));

    // then
    assertErrorCode(thrown, ErrorCode.SALE_END_AFTER_PERFORMANCE);
  }

  @Nested
  @DisplayName("공연 정보 수정")
  class Update {

    private static final LocalDateTime ON_SALE_NOW = SALE_START_AT.plusDays(1);
    private static final SalePeriod SALE_PERIOD = new SalePeriod(SALE_START_AT, SALE_END_AT);

    private Performance performance;

    @BeforeEach
    void setUp() {
      performance = create(SALE_START_AT, SALE_END_AT);
    }

    @ParameterizedTest
    @DisplayName("공연 일시가 현재 시각과 같거나 지났으면 수정할 수 없다")
    @ValueSource(longs = {0, 1})
    void rejectsUpdateWhenPerformanceStarted(long minutesAfterPerformance) {
      // given
      LocalDateTime now = PERFORMANCE_AT.plusMinutes(minutesAfterPerformance);

      // when
      Throwable thrown = catchThrowable(() -> update(PERFORMANCE_AT, SALE_PERIOD, false, now));

      // then
      assertErrorCode(thrown, ErrorCode.PERFORMANCE_ALREADY_STARTED);
    }

    @Test
    @DisplayName("예약이 있으면 공연 일시를 변경할 수 없다")
    void rejectsPerformanceAtChangeWhenReserved() {
      // when
      Throwable thrown =
          catchThrowable(
              () -> update(PERFORMANCE_AT.plusMinutes(1), SALE_PERIOD, true, ON_SALE_NOW));

      // then
      assertErrorCode(thrown, ErrorCode.SCHEDULE_CHANGE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("공연 일시가 지난 공연이면 일정 변경 불가 오류보다 공연 시작 오류를 먼저 반환한다")
    void rejectsStartedPerformanceBeforeScheduleChangeCheck() {
      // when
      Throwable thrown =
          catchThrowable(
              () -> update(PERFORMANCE_AT.plusMinutes(1), SALE_PERIOD, true, PERFORMANCE_AT));

      // then
      assertErrorCode(thrown, ErrorCode.PERFORMANCE_ALREADY_STARTED);
    }

    @Test
    @DisplayName("공연 일시가 지난 공연이면 판매 기간 오류보다 공연 시작 오류를 먼저 반환한다")
    void rejectsStartedPerformanceBeforeSalePeriodCheck() {
      // when
      Throwable thrown =
          catchThrowable(
              () ->
                  performance.update(
                      "공연",
                      "공연장",
                      PERFORMANCE_AT,
                      50_000,
                      SALE_START_AT,
                      SALE_START_AT,
                      false,
                      PERFORMANCE_AT));

      // then
      assertErrorCode(thrown, ErrorCode.PERFORMANCE_ALREADY_STARTED);
    }

    @Test
    @DisplayName("예약이 있으면 판매 시작 일시를 변경할 수 없다")
    void rejectsSaleStartAtChangeWhenReserved() {
      // given
      SalePeriod salePeriod = new SalePeriod(SALE_START_AT.plusMinutes(1), SALE_END_AT);

      // when
      Throwable thrown = catchThrowable(() -> update(PERFORMANCE_AT, salePeriod, true, NOW));

      // then
      assertErrorCode(thrown, ErrorCode.SCHEDULE_CHANGE_NOT_ALLOWED);
    }

    @ParameterizedTest
    @DisplayName("바꾼 공연 일시가 현재 시각과 같거나 빠르면 거절한다")
    @ValueSource(longs = {0, 1})
    void rejectsChangedPerformanceAtOrBeforeNow(long minutesBeforeNow) {
      // given
      LocalDateTime saleEndedNow = SALE_END_AT.plusDays(1);
      LocalDateTime performanceAt = saleEndedNow.minusMinutes(minutesBeforeNow);

      // when
      Throwable thrown =
          catchThrowable(() -> update(performanceAt, SALE_PERIOD, false, saleEndedNow));

      // then
      assertErrorCode(thrown, ErrorCode.INVALID_PERFORMANCE_AT);
    }

    @ParameterizedTest
    @DisplayName("바꾼 판매 시작 일시가 현재 시각과 같거나 빠르면 거절한다")
    @ValueSource(longs = {0, 1})
    void rejectsChangedSaleStartAtOrBeforeNow(long minutesBeforeNow) {
      // given
      SalePeriod salePeriod =
          new SalePeriod(ON_SALE_NOW.minusMinutes(minutesBeforeNow), SALE_END_AT);

      // when
      Throwable thrown =
          catchThrowable(() -> update(PERFORMANCE_AT, salePeriod, false, ON_SALE_NOW));

      // then
      assertErrorCode(thrown, ErrorCode.INVALID_SALE_START_AT);
    }

    @ParameterizedTest
    @DisplayName("바꾼 판매 종료 일시가 현재 시각과 같거나 빠르면 거절한다")
    @ValueSource(longs = {0, 1})
    void rejectsChangedSaleEndAtOrBeforeNow(long minutesBeforeNow) {
      // given
      SalePeriod salePeriod =
          new SalePeriod(SALE_START_AT, ON_SALE_NOW.minusMinutes(minutesBeforeNow));

      // when
      Throwable thrown =
          catchThrowable(() -> update(PERFORMANCE_AT, salePeriod, false, ON_SALE_NOW));

      // then
      assertErrorCode(thrown, ErrorCode.INVALID_SALE_END_AT);
    }

    @Test
    @DisplayName("판매 종료 일시가 공연 일시와 같으면 거절한다")
    void rejectsSaleEndAtPerformance() {
      // given
      SalePeriod salePeriod = new SalePeriod(SALE_START_AT, PERFORMANCE_AT);

      // when
      Throwable thrown = catchThrowable(() -> update(PERFORMANCE_AT, salePeriod, false, NOW));

      // then
      assertErrorCode(thrown, ErrorCode.SALE_END_AFTER_PERFORMANCE);
    }

    @Test
    @DisplayName("예약이 없으면 모든 항목을 수정할 수 있다")
    void updatesAllFieldsWithoutReservation() {
      // given
      LocalDateTime performanceAt = PERFORMANCE_AT.plusDays(1);
      SalePeriod salePeriod = new SalePeriod(SALE_START_AT.plusDays(1), SALE_END_AT.minusDays(1));

      // when
      performance.update(
          "새 공연",
          "새 공연장",
          performanceAt,
          60_000,
          salePeriod.saleStartAt(),
          salePeriod.saleEndAt(),
          false,
          NOW);

      // then
      assertThat(performance.getTitle()).isEqualTo("새 공연");
      assertThat(performance.getVenue()).isEqualTo("새 공연장");
      assertThat(performance.getPerformanceAt()).isEqualTo(performanceAt);
      assertThat(performance.getPrice()).isEqualTo(60_000);
      assertThat(performance.getSalePeriod()).isEqualTo(salePeriod);
    }

    @Test
    @DisplayName("예약이 있어도 공연 일시 전이면 공연 일시와 판매 시작 일시를 제외한 항목을 수정할 수 있다")
    void updatesFieldsExceptScheduleBeforePerformanceWhenReserved() {
      // given
      LocalDateTime now = PERFORMANCE_AT.minusMinutes(1);
      SalePeriod salePeriod = new SalePeriod(SALE_START_AT, now.plusSeconds(30));

      // when
      performance.update(
          "새 공연",
          "새 공연장",
          PERFORMANCE_AT,
          60_000,
          salePeriod.saleStartAt(),
          salePeriod.saleEndAt(),
          true,
          now);

      // then
      assertThat(performance.getTitle()).isEqualTo("새 공연");
      assertThat(performance.getVenue()).isEqualTo("새 공연장");
      assertThat(performance.getPrice()).isEqualTo(60_000);
      assertThat(performance.getSalePeriod()).isEqualTo(salePeriod);
    }

    @Test
    @DisplayName("공연 정보를 수정하면 버전이 1 올라간다")
    void increasesVersionOnUpdate() {
      // when
      update(PERFORMANCE_AT, SALE_PERIOD, false, NOW);

      // then
      assertThat(performance.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("수정 검증에 실패하면 버전이 바뀌지 않는다")
    void keepsVersionWhenUpdateIsRejected() {
      // when
      catchThrowable(() -> update(PERFORMANCE_AT, SALE_PERIOD, false, PERFORMANCE_AT));

      // then
      assertThat(performance.getVersion()).isZero();
    }

    private void update(
        LocalDateTime performanceAt,
        SalePeriod salePeriod,
        boolean hasReservation,
        LocalDateTime now) {
      performance.update(
          "공연",
          "공연장",
          performanceAt,
          50_000,
          salePeriod.saleStartAt(),
          salePeriod.saleEndAt(),
          hasReservation,
          now);
    }
  }

  private Performance create(LocalDateTime saleStartAt, LocalDateTime saleEndAt) {
    return Performance.create(
        "공연",
        "공연장",
        PerformanceTest.PERFORMANCE_AT,
        50_000,
        new SalePeriod(saleStartAt, saleEndAt),
        NOW);
  }

  private void assertErrorCode(Throwable thrown, ErrorCode errorCode) {
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
  }
}
