package com.ticketing.reservation.reservation.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.ticketing.reservation.reservation.repository.ReservationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationExpirySchedulerTest {

  private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 1, 12, 0);

  @Mock private ReservationRepository reservationRepository;
  @Mock private ReservationService reservationService;

  private ReservationExpiryScheduler expiryScheduler;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
    expiryScheduler =
        new ReservationExpiryScheduler(reservationRepository, reservationService, clock);
  }

  @Test
  @DisplayName("한 공연의 만료 처리가 실패해도 다른 공연을 만료한다")
  void continuesAfterFailure() {
    // given
    given(reservationRepository.findPerformanceIdsWithOverdue(any())).willReturn(List.of(1L, 2L));
    willThrow(new IllegalStateException("데이터 어긋남")).given(reservationService).expireOverdue(1L);

    // when
    expiryScheduler.expireOverdue();

    // then
    then(reservationService).should().expireOverdue(2L);
  }
}
