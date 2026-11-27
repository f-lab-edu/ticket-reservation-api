package com.ticketing.reservation.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import com.ticketing.reservation.performance.domain.Performance;
import com.ticketing.reservation.performance.domain.PerformanceInfo;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class ReservationTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 1, 12, 0);

  @Test
  @DisplayName("예약하면 결제 대기 상태로 예약 당시 가격과 수량을 기록하고 예약 금액을 계산한다")
  void recordsPriceAndPendingStatus() {
    // given
    Performance performance = performance(NOW.plusDays(10), 50_000);

    // when
    Reservation reservation = Reservation.create(7L, performance, 3, NOW);

    // then
    assertThat(reservation.getMemberId()).isEqualTo(7L);
    assertThat(reservation.getPerformanceId()).isEqualTo(1L);
    assertThat(reservation.getQuantity()).isEqualTo(3);
    assertThat(reservation.getUnitPrice()).isEqualTo(50_000);
    assertThat(reservation.getTotalPrice()).isEqualTo(150_000L);
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
    assertThat(reservation.getReservedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("단가와 수량이 모두 int 최대값이어도 예약 금액을 정확히 계산한다")
  void calculatesTotalPriceWithMaxPriceAndQuantity() {
    // given
    Performance performance = performance(NOW.plusDays(10), Integer.MAX_VALUE);

    // when
    Reservation reservation = Reservation.create(7L, performance, Integer.MAX_VALUE, NOW);

    // then
    assertThat(reservation.getTotalPrice()).isEqualTo(4_611_686_014_132_420_609L);
  }

  @Test
  @DisplayName("결제 제한 시각은 예약 시각으로부터 10분 후이다")
  void paymentDeadlineIsTenMinutesLater() {
    // given
    Performance performance = performance(NOW.plusDays(10), 50_000);

    // when
    Reservation reservation = Reservation.create(7L, performance, 1, NOW);

    // then
    assertThat(reservation.getPaymentDeadlineAt()).isEqualTo(NOW.plusMinutes(10));
  }

  @Test
  @DisplayName("공연 일시가 10분 후보다 빠르면 결제 제한 시각은 공연 일시다")
  void paymentDeadlineIsPerformanceAtWhenEarlier() {
    // given
    LocalDateTime performanceAt = NOW.plusMinutes(5);
    Performance performance = performance(performanceAt, 50_000);

    // when
    Reservation reservation = Reservation.create(7L, performance, 1, NOW);

    // then
    assertThat(reservation.getPaymentDeadlineAt()).isEqualTo(performanceAt);
  }

  @Test
  @DisplayName("예약 일시는 마이크로초 단위로 자른다")
  void truncatesReservedAtToMicros() {
    // given
    Performance performance = performance(NOW.plusDays(10), 50_000);
    LocalDateTime now = NOW.plusNanos(123_456_789);

    // when
    Reservation reservation = Reservation.create(7L, performance, 1, now);

    // then
    assertThat(reservation.getReservedAt()).isEqualTo(NOW.plusNanos(123_456_000));
    assertThat(reservation.getPaymentDeadlineAt())
        .isEqualTo(NOW.plusMinutes(10).plusNanos(123_456_000));
  }

  @Test
  @DisplayName("판매 기간이 아니면 예약을 만들 수 없다")
  void rejectsWhenNotOnSale() {
    // given
    Performance performance = performance(NOW.plusDays(10), 50_000);
    LocalDateTime beforeSaleStart = performance.getSalePeriod().saleStartAt().minusSeconds(1);

    // when
    Throwable thrown =
        catchThrowable(() -> Reservation.create(7L, performance, 1, beforeSaleStart));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_ON_SALE));
  }

  @Test
  @DisplayName("결제 제한 시각 전에 조회하면 결제 대기 상태를 반환한다")
  void isPendingBeforeDeadline() {
    // given
    Reservation reservation = Reservation.create(7L, performance(NOW.plusDays(10), 50_000), 1, NOW);

    // when
    ReservationStatus status =
        reservation.statusAt(reservation.getPaymentDeadlineAt().minus(1, ChronoUnit.MICROS));

    // then
    assertThat(status).isEqualTo(ReservationStatus.PENDING_PAYMENT);
  }

  @ParameterizedTest(name = "제한 시각 + {0}μs")
  @DisplayName("결제 제한 시각부터 조회하면 만료 상태를 반환한다")
  @ValueSource(longs = {0, 1})
  void isExpiredFromDeadline(long microsAfterDeadline) {
    // given
    Reservation reservation = Reservation.create(7L, performance(NOW.plusDays(10), 50_000), 1, NOW);

    // when
    ReservationStatus status =
        reservation.statusAt(
            reservation.getPaymentDeadlineAt().plus(microsAfterDeadline, ChronoUnit.MICROS));

    // then
    assertThat(status).isEqualTo(ReservationStatus.EXPIRED);
  }

  @Test
  @DisplayName("결제 제한 시각에 도달하면 만료된다")
  void expiresFromDeadline() {
    // given
    Reservation reservation = Reservation.create(7L, performance(NOW.plusDays(10), 50_000), 1, NOW);

    // when
    reservation.expire(reservation.getPaymentDeadlineAt());

    // then
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
  }

  @Test
  @DisplayName("결제 제한 시각 전에는 만료할 수 없다")
  void rejectsExpireBeforeDeadline() {
    // given
    Reservation reservation = Reservation.create(7L, performance(NOW.plusDays(10), 50_000), 1, NOW);

    // when
    Throwable thrown =
        catchThrowable(
            () ->
                reservation.expire(reservation.getPaymentDeadlineAt().minus(1, ChronoUnit.MICROS)));

    // then
    assertThat(thrown).isInstanceOf(IllegalStateException.class);
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
  }

  @Test
  @DisplayName("이미 만료된 예약은 다시 만료할 수 없다")
  void rejectsExpireTwice() {
    // given
    Reservation reservation = Reservation.create(7L, performance(NOW.plusDays(10), 50_000), 1, NOW);
    reservation.expire(reservation.getPaymentDeadlineAt());

    // when
    Throwable thrown = catchThrowable(() -> reservation.expire(reservation.getPaymentDeadlineAt()));

    // then
    assertThat(thrown).isInstanceOf(IllegalStateException.class);
  }

  private static Performance performance(LocalDateTime performanceAt, int price) {
    LocalDateTime saleStartAt = NOW.minusDays(1);
    Performance performance =
        Performance.create(
            new PerformanceInfo(
                "공연", "공연장", performanceAt, price, saleStartAt, performanceAt.minusMinutes(1)),
            saleStartAt.minusDays(1));
    ReflectionTestUtils.setField(performance, "id", 1L);
    return performance;
  }
}
