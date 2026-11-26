package com.ticketing.reservation.reservation.domain;

import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import com.ticketing.reservation.performance.domain.Performance;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
public class Reservation {

  public static final Duration PAYMENT_TIME_LIMIT = Duration.ofMinutes(10);

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long memberId;

  private Long performanceId;

  private int quantity;

  private int unitPrice;

  @Enumerated(EnumType.STRING)
  private ReservationStatus status;

  private LocalDateTime reservedAt;

  private LocalDateTime paymentDeadlineAt;

  protected Reservation() {}

  private Reservation(
      Long memberId,
      Long performanceId,
      int quantity,
      int unitPrice,
      LocalDateTime reservedAt,
      LocalDateTime paymentDeadlineAt) {
    this.memberId = memberId;
    this.performanceId = performanceId;
    this.quantity = quantity;
    this.unitPrice = unitPrice;
    this.status = ReservationStatus.PENDING_PAYMENT;
    this.reservedAt = reservedAt;
    this.paymentDeadlineAt = paymentDeadlineAt;
  }

  public static Reservation create(
      Long memberId, Performance performance, int quantity, LocalDateTime now) {
    if (!performance.getSalePeriod().isOnSale(now)) {
      throw new BusinessException(ErrorCode.NOT_ON_SALE);
    }
    LocalDateTime reservedAt = now.truncatedTo(ChronoUnit.MICROS);
    return new Reservation(
        memberId,
        performance.getId(),
        quantity,
        performance.getPrice(),
        reservedAt,
        paymentDeadline(performance.getPerformanceAt(), reservedAt));
  }

  private static LocalDateTime paymentDeadline(LocalDateTime performanceAt, LocalDateTime now) {
    LocalDateTime timeLimit = now.plus(PAYMENT_TIME_LIMIT);
    return timeLimit.isBefore(performanceAt) ? timeLimit : performanceAt;
  }

  public ReservationStatus statusAt(LocalDateTime now) {
    if (status == ReservationStatus.PENDING_PAYMENT && !now.isBefore(paymentDeadlineAt)) {
      return ReservationStatus.EXPIRED;
    }
    return status;
  }

  public Long getId() {
    return id;
  }

  public Long getMemberId() {
    return memberId;
  }

  public Long getPerformanceId() {
    return performanceId;
  }

  public int getQuantity() {
    return quantity;
  }

  public int getUnitPrice() {
    return unitPrice;
  }

  public long getTotalPrice() {
    return (long) unitPrice * quantity;
  }

  public ReservationStatus getStatus() {
    return status;
  }

  public LocalDateTime getReservedAt() {
    return reservedAt;
  }

  public LocalDateTime getPaymentDeadlineAt() {
    return paymentDeadlineAt;
  }
}
