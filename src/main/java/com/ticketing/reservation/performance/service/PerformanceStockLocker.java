package com.ticketing.reservation.performance.service;

import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import com.ticketing.reservation.performance.domain.PerformanceStock;
import com.ticketing.reservation.performance.repository.PerformanceStockRepository;
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

  public PerformanceStockLocker(PerformanceStockRepository stockRepository) {
    this.stockRepository = stockRepository;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public PerformanceStock lock(Long performanceId) {
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
