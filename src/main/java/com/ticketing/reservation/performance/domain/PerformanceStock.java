package com.ticketing.reservation.performance.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class PerformanceStock {

  @Id private Long performanceId;

  private int totalQuantity;

  private int reservedQuantity;

  protected PerformanceStock() {}

  private PerformanceStock(Long performanceId, int totalQuantity) {
    this.performanceId = performanceId;
    this.totalQuantity = totalQuantity;
  }

  public static PerformanceStock create(Long performanceId, int totalQuantity) {
    return new PerformanceStock(performanceId, totalQuantity);
  }

  public Long getPerformanceId() {
    return performanceId;
  }

  public int getTotalQuantity() {
    return totalQuantity;
  }

  public int getRemainingQuantity() {
    return totalQuantity - reservedQuantity;
  }
}
