package com.ticketing.reservation.reservation.service;

import com.ticketing.reservation.performance.domain.PerformanceStock;
import com.ticketing.reservation.performance.service.OverdueReservationExpirer;
import com.ticketing.reservation.reservation.domain.Reservation;
import com.ticketing.reservation.reservation.repository.ReservationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReservationExpirer implements OverdueReservationExpirer {

  private static final Logger log = LoggerFactory.getLogger(ReservationExpirer.class);

  private final ReservationRepository reservationRepository;

  public ReservationExpirer(ReservationRepository reservationRepository) {
    this.reservationRepository = reservationRepository;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void expireOverdue(PerformanceStock stock, LocalDateTime now) {
    List<Reservation> overdue =
        reservationRepository.findOverdueForUpdate(stock.getPerformanceId(), now);
    if (overdue.isEmpty()) {
      return;
    }
    overdue.forEach(reservation -> reservation.expire(now));
    int quantity = overdue.stream().mapToInt(Reservation::getQuantity).sum();
    stock.release(quantity);
    log.info(
        "Expired overdue reservations: performanceId={}, count={}, quantity={}",
        stock.getPerformanceId(),
        overdue.size(),
        quantity);
  }
}
