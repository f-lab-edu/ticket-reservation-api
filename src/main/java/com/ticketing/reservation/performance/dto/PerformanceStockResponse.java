package com.ticketing.reservation.performance.dto;

import com.ticketing.reservation.performance.domain.PerformanceStock;

public record PerformanceStockResponse(Long performanceId, int remainingQuantity) {

  public static PerformanceStockResponse from(PerformanceStock stock) {
    return new PerformanceStockResponse(stock.getPerformanceId(), stock.getRemainingQuantity());
  }
}
