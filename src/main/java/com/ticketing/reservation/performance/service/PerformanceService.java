package com.ticketing.reservation.performance.service;

import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import com.ticketing.reservation.performance.domain.Performance;
import com.ticketing.reservation.performance.domain.SalePeriod;
import com.ticketing.reservation.performance.dto.PerformanceRequest;
import com.ticketing.reservation.performance.repository.PerformanceRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PerformanceService {

  private final PerformanceRepository performanceRepository;
  private final Clock clock;

  public PerformanceService(PerformanceRepository performanceRepository, Clock clock) {
    this.performanceRepository = performanceRepository;
    this.clock = clock;
  }

  @Transactional
  public Performance register(PerformanceRequest request) {
    Performance performance =
        Performance.create(
            request.title(),
            request.venue(),
            request.performanceAt(),
            request.price(),
            request.totalQuantity(),
            new SalePeriod(request.saleStartAt(), request.saleEndAt()),
            LocalDateTime.now(clock));
    return performanceRepository.save(performance);
  }

  @Transactional(readOnly = true)
  public Performance getPerformance(Long performanceId) {
    return performanceRepository
        .findById(performanceId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PERFORMANCE_NOT_FOUND));
  }

  @Transactional(readOnly = true)
  public List<Performance> getPerformances(boolean available) {
    if (available) {
      return performanceRepository.findAvailable(LocalDateTime.now(clock));
    }
    return performanceRepository.findAllByOrderByPerformanceAtAscIdAsc();
  }
}
