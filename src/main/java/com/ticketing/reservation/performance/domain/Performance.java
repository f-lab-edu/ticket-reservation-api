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

  private long version;

  protected Performance() {}

  private Performance(
      String title, String venue, LocalDateTime performanceAt, int price, SalePeriod salePeriod) {
    this.title = title;
    this.venue = venue;
    this.performanceAt = performanceAt;
    this.price = price;
    this.salePeriod = salePeriod;
  }

  public static Performance create(PerformanceInfo info, LocalDateTime now) {
    SalePeriod salePeriod = new SalePeriod(info.saleStartAt(), info.saleEndAt());
    validateSaleStartAt(salePeriod, now);
    validateSaleEndBeforePerformance(salePeriod, info.performanceAt());
    return new Performance(
        info.title(), info.venue(), info.performanceAt(), info.price(), salePeriod);
  }

  private void validateUpdatable(
      LocalDateTime performanceAt,
      LocalDateTime saleStartAt,
      boolean hasReservation,
      LocalDateTime now) {
    if (!this.performanceAt.isAfter(now)) {
      throw new BusinessException(ErrorCode.PERFORMANCE_ALREADY_STARTED);
    }
    boolean scheduleChanged =
        !performanceAt.isEqual(this.performanceAt)
            || !saleStartAt.isEqual(this.salePeriod.saleStartAt());
    if (hasReservation && scheduleChanged) {
      throw new BusinessException(ErrorCode.SCHEDULE_CHANGE_NOT_ALLOWED);
    }
  }

  public void update(PerformanceInfo changes, boolean hasReservation, LocalDateTime now) {
    validateUpdatable(changes.performanceAt(), changes.saleStartAt(), hasReservation, now);

    SalePeriod newSalePeriod = new SalePeriod(changes.saleStartAt(), changes.saleEndAt());
    boolean performanceAtChanged = !changes.performanceAt().isEqual(this.performanceAt);
    boolean saleStartChanged = !newSalePeriod.saleStartAt().isEqual(this.salePeriod.saleStartAt());
    boolean saleEndChanged = !newSalePeriod.saleEndAt().isEqual(this.salePeriod.saleEndAt());

    if (performanceAtChanged && !changes.performanceAt().isAfter(now)) {
      throw new BusinessException(ErrorCode.INVALID_PERFORMANCE_AT);
    }
    if (saleStartChanged) {
      validateSaleStartAt(newSalePeriod, now);
    }
    if (saleEndChanged && !newSalePeriod.saleEndAt().isAfter(now)) {
      throw new BusinessException(ErrorCode.INVALID_SALE_END_AT);
    }
    validateSaleEndBeforePerformance(newSalePeriod, changes.performanceAt());

    this.title = changes.title();
    this.venue = changes.venue();
    this.performanceAt = changes.performanceAt();
    this.price = changes.price();
    this.salePeriod = newSalePeriod;
    this.version++;
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

  public long getVersion() {
    return version;
  }
}
