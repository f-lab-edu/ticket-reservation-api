package com.ticketing.reservation.performance.service;

import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import com.ticketing.reservation.global.web.IfMatch;
import com.ticketing.reservation.performance.domain.Performance;
import com.ticketing.reservation.performance.domain.PerformanceDetail;
import com.ticketing.reservation.performance.domain.PerformanceInfo;
import com.ticketing.reservation.performance.domain.PerformanceStock;
import com.ticketing.reservation.performance.repository.PerformanceRepository;
import com.ticketing.reservation.performance.repository.PerformanceStockRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PerformanceService {

  private final PerformanceRepository performanceRepository;
  private final PerformanceStockRepository stockRepository;
  private final Clock clock;

  public PerformanceService(
      PerformanceRepository performanceRepository,
      PerformanceStockRepository stockRepository,
      Clock clock) {
    this.performanceRepository = performanceRepository;
    this.stockRepository = stockRepository;
    this.clock = clock;
  }

  @Transactional
  public PerformanceDetail register(PerformanceInfo info, int totalQuantity) {
    Performance performance =
        performanceRepository.save(Performance.create(info, LocalDateTime.now(clock)));
    PerformanceStock stock =
        stockRepository.save(PerformanceStock.create(performance.getId(), totalQuantity));
    return new PerformanceDetail(performance, stock);
  }

  @Transactional
  public PerformanceDetail update(
      Long performanceId, PerformanceInfo info, int totalQuantity, IfMatch ifMatch) {
    Performance performance =
        performanceRepository
            .findByIdForUpdate(performanceId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PERFORMANCE_NOT_FOUND));
    if (!ifMatch.matches(performance.getVersion())) {
      throw new BusinessException(ErrorCode.PERFORMANCE_MODIFIED);
    }
    PerformanceStock stock = findStock(performanceId);
    performance.update(info, stock.hasReservation(), LocalDateTime.now(clock));
    stock.changeTotalQuantity(totalQuantity);
    return new PerformanceDetail(performance, stock);
  }

  @Transactional(readOnly = true)
  public PerformanceDetail getPerformance(Long performanceId) {
    return new PerformanceDetail(findPerformance(performanceId), findStock(performanceId));
  }

  @Transactional(readOnly = true)
  public List<PerformanceDetail> getPerformances(boolean available) {
    List<Performance> performances = findPerformances(available);
    Map<Long, PerformanceStock> stocks = findStocksOf(performances);
    return performances.stream()
        .map(
            performance ->
                new PerformanceDetail(
                    performance,
                    Optional.ofNullable(stocks.get(performance.getId()))
                        .orElseThrow(() -> stockMissing(performance.getId()))))
        .toList();
  }

  private Performance findPerformance(Long performanceId) {
    return performanceRepository
        .findById(performanceId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PERFORMANCE_NOT_FOUND));
  }

  private PerformanceStock findStock(Long performanceId) {
    return stockRepository.findById(performanceId).orElseThrow(() -> stockMissing(performanceId));
  }

  private List<Performance> findPerformances(boolean available) {
    return available
        ? performanceRepository.findAvailable(LocalDateTime.now(clock))
        : performanceRepository.findAllByOrderByPerformanceAtAscIdAsc();
  }

  private Map<Long, PerformanceStock> findStocksOf(List<Performance> performances) {
    List<Long> performanceIds = performances.stream().map(Performance::getId).toList();
    return stockRepository.findAllById(performanceIds).stream()
        .collect(Collectors.toMap(PerformanceStock::getPerformanceId, Function.identity()));
  }

  private IllegalStateException stockMissing(Long performanceId) {
    return new IllegalStateException("공연 재고가 없습니다. performanceId=" + performanceId);
  }
}
