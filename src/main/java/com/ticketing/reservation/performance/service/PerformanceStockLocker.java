package com.ticketing.reservation.performance.service;

import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import com.ticketing.reservation.performance.domain.PerformanceStock;
import com.ticketing.reservation.performance.repository.PerformanceStockRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PerformanceStockLocker {

  private static final Logger log = LoggerFactory.getLogger(PerformanceStockLocker.class);

  private final PerformanceStockRepository stockRepository;
  private final OverdueReservationExpirer overdueReservationExpirer;
  private final Clock clock;

  public PerformanceStockLocker(
      PerformanceStockRepository stockRepository,
      OverdueReservationExpirer overdueReservationExpirer,
      Clock clock) {
    this.stockRepository = stockRepository;
    this.overdueReservationExpirer = overdueReservationExpirer;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public PerformanceStock lockAndExpireOverdue(Long performanceId) {
    PerformanceStock stock = lock(performanceId);
    overdueReservationExpirer.expireOverdue(stock, now());
    return stock;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void tryLockAndExpireOverdue(Long performanceId) {
    stockRepository
        .findByIdForUpdateSkipLocked(performanceId)
        .ifPresent(stock -> overdueReservationExpirer.expireOverdue(stock, now()));
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
  }

  private PerformanceStock lock(Long performanceId) {
    try {
      return stockRepository
          .findByIdForUpdate(performanceId)
          .orElseThrow(() -> new BusinessException(ErrorCode.PERFORMANCE_NOT_FOUND));
    } catch (PessimisticLockingFailureException e) {
      log.warn("Failed to acquire performance stock lock: performanceId={}", performanceId, e);
      throw new BusinessException(ErrorCode.STOCK_BUSY);
    }
  }
}
