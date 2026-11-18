package com.ticketing.reservation.reservation.service;

import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import com.ticketing.reservation.performance.domain.Performance;
import com.ticketing.reservation.performance.domain.PerformanceStock;
import com.ticketing.reservation.performance.repository.PerformanceRepository;
import com.ticketing.reservation.performance.service.PerformanceStockLocker;
import com.ticketing.reservation.reservation.domain.Reservation;
import com.ticketing.reservation.reservation.repository.ReservationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {

  private final ReservationRepository reservationRepository;
  private final PerformanceRepository performanceRepository;
  private final PerformanceStockLocker stockLocker;
  private final Clock clock;

  public ReservationService(
      ReservationRepository reservationRepository,
      PerformanceRepository performanceRepository,
      PerformanceStockLocker stockLocker,
      Clock clock) {
    this.reservationRepository = reservationRepository;
    this.performanceRepository = performanceRepository;
    this.stockLocker = stockLocker;
    this.clock = clock;
  }

  @Transactional
  public Reservation reserve(Long memberId, Long performanceId, int quantity) {
    PerformanceStock stock = stockLocker.lock(performanceId);
    Performance performance =
        performanceRepository
            .findById(performanceId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PERFORMANCE_NOT_FOUND));
    Reservation reservation =
        Reservation.create(memberId, performance, quantity, LocalDateTime.now(clock));
    stock.reserve(quantity);
    return reservationRepository.save(reservation);
  }
}
