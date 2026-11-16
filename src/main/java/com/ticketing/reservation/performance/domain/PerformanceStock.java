package com.ticketing.reservation.performance.domain;

import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
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

  public void changeTotalQuantity(int totalQuantity) {
    if (totalQuantity < reservedQuantity) {
      throw new BusinessException(ErrorCode.TOTAL_QUANTITY_BELOW_RESERVED);
    }
    this.totalQuantity = totalQuantity;
  }

  public boolean hasReservation() {
    return reservedQuantity > 0;
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
