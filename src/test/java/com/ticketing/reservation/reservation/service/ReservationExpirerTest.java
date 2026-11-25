package com.ticketing.reservation.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.ticketing.reservation.performance.domain.Performance;
import com.ticketing.reservation.performance.domain.PerformanceInfo;
import com.ticketing.reservation.performance.domain.PerformanceStock;
import com.ticketing.reservation.reservation.domain.Reservation;
import com.ticketing.reservation.reservation.domain.ReservationStatus;
import com.ticketing.reservation.reservation.repository.ReservationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReservationExpirerTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 1, 12, 0);

  @Mock private ReservationRepository reservationRepository;

  @InjectMocks private ReservationExpirer reservationExpirer;

  @Test
  @DisplayName("만료 대상 예약을 모두 만료하고 수량의 합만큼 재고를 복구한다")
  void expiresOverdueAndReleasesTotalQuantity() {
    // given
    Performance performance = performance();
    Reservation first = overdue(performance, 2);
    Reservation second = overdue(performance, 3);
    PerformanceStock stock = stockWithReserved(5);
    given(reservationRepository.findOverdueForUpdate(1L, NOW)).willReturn(List.of(first, second));

    // when
    reservationExpirer.expireOverdue(stock, NOW);

    // then
    assertThat(first.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
    assertThat(second.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
    assertThat(stock.getReservedQuantity()).isZero();
  }

  private static Performance performance() {
    LocalDateTime performanceAt = NOW.plusDays(10);
    LocalDateTime saleStartAt = NOW.minusDays(1);
    Performance performance =
        Performance.create(
            new PerformanceInfo(
                "공연", "공연장", performanceAt, 50_000, saleStartAt, performanceAt.minusMinutes(1)),
            saleStartAt.minusDays(1));
    ReflectionTestUtils.setField(performance, "id", 1L);
    return performance;
  }

  private static Reservation overdue(Performance performance, int quantity) {
    return Reservation.create(7L, performance, quantity, NOW.minusMinutes(11));
  }

  private static PerformanceStock stockWithReserved(int reservedQuantity) {
    PerformanceStock stock = PerformanceStock.create(1L, 100);
    stock.reserve(reservedQuantity);
    return stock;
  }
}
