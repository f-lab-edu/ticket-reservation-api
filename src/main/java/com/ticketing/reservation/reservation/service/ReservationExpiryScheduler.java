package com.ticketing.reservation.reservation.service;

import com.ticketing.reservation.reservation.repository.ReservationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationExpiryScheduler {

  private static final Logger log = LoggerFactory.getLogger(ReservationExpiryScheduler.class);

  private final ReservationRepository reservationRepository;
  private final ReservationService reservationService;
  private final Clock clock;

  public ReservationExpiryScheduler(
      ReservationRepository reservationRepository,
      ReservationService reservationService,
      Clock clock) {
    this.reservationRepository = reservationRepository;
    this.reservationService = reservationService;
    this.clock = clock;
  }

  @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.SECONDS)
  public void expireOverdue() {
    LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    for (Long performanceId : reservationRepository.findPerformanceIdsWithOverdue(now)) {
      try {
        reservationService.expireOverdue(performanceId);
      } catch (RuntimeException e) {
        log.error(
            "Unexpected error while expiring overdue reservations: performanceId={}",
            performanceId,
            e);
      }
    }
  }
}
