package com.ticketing.reservation.performance.domain;

import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class Performance {

  public static final int MAX_TITLE_LENGTH = 100;
  public static final int MAX_VENUE_LENGTH = 100;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String title;

  private String venue;

  private LocalDateTime performanceAt;

  private int price;

  @Embedded private SalePeriod salePeriod;

  protected Performance() {}

  private Performance(
      String title, String venue, LocalDateTime performanceAt, int price, SalePeriod salePeriod) {
    this.title = title;
    this.venue = venue;
    this.performanceAt = performanceAt;
    this.price = price;
    this.salePeriod = salePeriod;
  }

  public static Performance create(
      String title,
      String venue,
      LocalDateTime performanceAt,
      int price,
      SalePeriod salePeriod,
      LocalDateTime now) {
    validateSaleStartAt(salePeriod, now);
    validateSaleEndBeforePerformance(salePeriod, performanceAt);
    return new Performance(title, venue, performanceAt, price, salePeriod);
  }

  public void update(
      String title,
      String venue,
      LocalDateTime performanceAt,
      int price,
      SalePeriod salePeriod,
      boolean hasReservation,
      LocalDateTime now) {
    boolean performanceAtChanged = !performanceAt.isEqual(this.performanceAt);
    boolean saleStartChanged = !salePeriod.saleStartAt().isEqual(this.salePeriod.saleStartAt());
    boolean saleEndChanged = !salePeriod.saleEndAt().isEqual(this.salePeriod.saleEndAt());

    if (!this.performanceAt.isAfter(now)) {
      throw new BusinessException(ErrorCode.PERFORMANCE_ALREADY_STARTED);
    }
    if (hasReservation && (performanceAtChanged || saleStartChanged)) {
      throw new BusinessException(ErrorCode.SCHEDULE_CHANGE_NOT_ALLOWED);
    }
    if (performanceAtChanged && !performanceAt.isAfter(now)) {
      throw new BusinessException(ErrorCode.INVALID_PERFORMANCE_AT);
    }
    if (saleStartChanged) {
      validateSaleStartAt(salePeriod, now);
    }
    if (saleEndChanged && !salePeriod.saleEndAt().isAfter(now)) {
      throw new BusinessException(ErrorCode.INVALID_SALE_END_AT);
    }
    validateSaleEndBeforePerformance(salePeriod, performanceAt);

    this.title = title;
    this.venue = venue;
    this.performanceAt = performanceAt;
    this.price = price;
    this.salePeriod = salePeriod;
  }

  private static void validateSaleStartAt(SalePeriod salePeriod, LocalDateTime now) {
    if (!salePeriod.saleStartAt().isAfter(now)) {
      throw new BusinessException(ErrorCode.INVALID_SALE_START_AT);
    }
  }

  private static void validateSaleEndBeforePerformance(
      SalePeriod salePeriod, LocalDateTime performanceAt) {
    if (!salePeriod.saleEndAt().isBefore(performanceAt)) {
      throw new BusinessException(ErrorCode.SALE_END_AFTER_PERFORMANCE);
    }
  }

  public Long getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getVenue() {
    return venue;
  }

  public LocalDateTime getPerformanceAt() {
    return performanceAt;
  }

  public int getPrice() {
    return price;
  }

  public SalePeriod getSalePeriod() {
    return salePeriod;
  }
}
