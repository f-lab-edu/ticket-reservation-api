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

  private int totalQuantity;

  private int remainingQuantity;

  @Embedded private SalePeriod salePeriod;

  protected Performance() {}

  private Performance(
      String title,
      String venue,
      LocalDateTime performanceAt,
      int price,
      int totalQuantity,
      SalePeriod salePeriod) {
    this.title = title;
    this.venue = venue;
    this.performanceAt = performanceAt;
    this.price = price;
    this.totalQuantity = totalQuantity;
    this.remainingQuantity = totalQuantity;
    this.salePeriod = salePeriod;
  }

  public static Performance create(
      String title,
      String venue,
      LocalDateTime performanceAt,
      int price,
      int totalQuantity,
      SalePeriod salePeriod,
      LocalDateTime now) {
    if (!salePeriod.saleStartAt().isAfter(now)) {
      throw new BusinessException(ErrorCode.INVALID_SALE_START_AT);
    }
    if (!salePeriod.saleEndAt().isBefore(performanceAt)) {
      throw new BusinessException(ErrorCode.SALE_END_AFTER_PERFORMANCE);
    }
    return new Performance(title, venue, performanceAt, price, totalQuantity, salePeriod);
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

  public int getTotalQuantity() {
    return totalQuantity;
  }

  public int getRemainingQuantity() {
    return remainingQuantity;
  }

  public SalePeriod getSalePeriod() {
    return salePeriod;
  }
}
