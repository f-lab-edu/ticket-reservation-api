package com.ticketing.reservation.performance.domain;

import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.Map;

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
      throw new BusinessException(
          ErrorCode.TOTAL_QUANTITY_BELOW_RESERVED, Map.of("reservedQuantity", reservedQuantity));
    }
    this.totalQuantity = totalQuantity;
  }

  public void reserve(int quantity) {
    if (quantity > getRemainingQuantity()) {
      throw new BusinessException(
          ErrorCode.INSUFFICIENT_STOCK, Map.of("remainingQuantity", getRemainingQuantity()));
    }
    this.reservedQuantity += quantity;
  }

  public void release(int quantity) {
    if (quantity < 0 || quantity > reservedQuantity) {
      throw new IllegalStateException(
          "복구할 수량은 0 이상 예약 수량 이하여야 합니다: quantity="
              + quantity
              + ", reservedQuantity="
              + reservedQuantity);
    }
    this.reservedQuantity -= quantity;
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

  public int getReservedQuantity() {
    return reservedQuantity;
  }

  public int getRemainingQuantity() {
    return totalQuantity - reservedQuantity;
  }
}
