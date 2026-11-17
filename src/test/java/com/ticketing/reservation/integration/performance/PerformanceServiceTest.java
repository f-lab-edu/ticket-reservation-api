package com.ticketing.reservation.integration.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;

import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import com.ticketing.reservation.integration.TestcontainersConfiguration;
import com.ticketing.reservation.performance.domain.Performance;
import com.ticketing.reservation.performance.domain.PerformanceDetail;
import com.ticketing.reservation.performance.domain.PerformanceStock;
import com.ticketing.reservation.performance.domain.SalePeriod;
import com.ticketing.reservation.performance.dto.PerformanceRequest;
import com.ticketing.reservation.performance.repository.PerformanceRepository;
import com.ticketing.reservation.performance.repository.PerformanceStockRepository;
import com.ticketing.reservation.performance.service.PerformanceService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PerformanceServiceTest {

  @Autowired private PerformanceService performanceService;
  @Autowired private PerformanceRepository performanceRepository;
  @Autowired private PerformanceStockRepository stockRepository;
  @Autowired private Clock clock;

  @AfterEach
  void tearDown() {
    stockRepository.deleteAllInBatch();
    performanceRepository.deleteAllInBatch();
  }

  @Test
  @DisplayName("공연을 등록하면 공연 정보와 재고를 저장한다")
  void savesPerformanceAndStock() {
    // given
    LocalDateTime performanceAt =
        LocalDateTime.now(clock).plusDays(30).truncatedTo(ChronoUnit.SECONDS);
    LocalDateTime saleStartAt = performanceAt.minusDays(29);
    LocalDateTime saleEndAt = performanceAt.minusDays(1);
    PerformanceRequest request =
        new PerformanceRequest("공연", "공연장", performanceAt, 50_000, 100, saleStartAt, saleEndAt);

    // when
    PerformanceDetail detail = performanceService.register(request);

    // then
    Long performanceId = detail.performance().getId();
    Performance saved = performanceRepository.findById(performanceId).orElseThrow();
    assertThat(saved.getTitle()).isEqualTo("공연");
    assertThat(saved.getVenue()).isEqualTo("공연장");
    assertThat(saved.getPerformanceAt()).isEqualTo(performanceAt);
    assertThat(saved.getPrice()).isEqualTo(50_000);
    assertThat(saved.getSalePeriod()).isEqualTo(new SalePeriod(saleStartAt, saleEndAt));
    PerformanceStock stock = stockRepository.findById(performanceId).orElseThrow();
    assertThat(stock.getTotalQuantity()).isEqualTo(100);
    assertThat(stock.getRemainingQuantity()).isEqualTo(100);
  }

  @Test
  @DisplayName("공연 정보와 재고를 수정한다")
  void updatesPerformanceAndStock() {
    // given
    LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    Performance performance = save("공연", now.plusDays(1), now.plusDays(10), 100);
    LocalDateTime performanceAt = now.plusDays(20);
    LocalDateTime saleStartAt = now.plusDays(2);
    LocalDateTime saleEndAt = now.plusDays(15);
    PerformanceRequest request =
        new PerformanceRequest("새 공연", "새 공연장", performanceAt, 60_000, 120, saleStartAt, saleEndAt);

    // when
    performanceService.update(performance.getId(), request);

    // then
    Performance saved = performanceRepository.findById(performance.getId()).orElseThrow();
    assertThat(saved.getTitle()).isEqualTo("새 공연");
    assertThat(saved.getVenue()).isEqualTo("새 공연장");
    assertThat(saved.getPerformanceAt()).isEqualTo(performanceAt);
    assertThat(saved.getPrice()).isEqualTo(60_000);
    assertThat(saved.getSalePeriod()).isEqualTo(new SalePeriod(saleStartAt, saleEndAt));
    PerformanceStock stock = stockRepository.findById(performance.getId()).orElseThrow();
    assertThat(stock.getTotalQuantity()).isEqualTo(120);
    assertThat(stock.getRemainingQuantity()).isEqualTo(120);
  }

  @Test
  @DisplayName("총 판매 수량 검증에 실패하면 공연 정보도 바뀌지 않는다")
  void keepsPerformanceWhenStockUpdateFails() {
    // given
    LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    Performance performance = save("공연", now.plusDays(1), now.plusDays(10), 100);
    reserve(performance, 30);
    PerformanceRequest request =
        new PerformanceRequest(
            "새 공연",
            "공연장",
            performance.getPerformanceAt(),
            50_000,
            29,
            now.plusDays(1),
            now.plusDays(10));

    // when
    Throwable thrown =
        catchThrowable(() -> performanceService.update(performance.getId(), request));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOTAL_QUANTITY_BELOW_RESERVED));
    Performance saved = performanceRepository.findById(performance.getId()).orElseThrow();
    assertThat(saved.getTitle()).isEqualTo("공연");
  }

  @Test
  @DisplayName("공연 일시가 지난 공연이면 총 판매 수량 오류보다 공연 시작 오류를 먼저 반환한다")
  void rejectsStartedPerformanceBeforeQuantityCheck() {
    // given
    LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    Performance performance = save("공연", now.minusDays(3), now.minusDays(2), 100);
    reserve(performance, 30);
    PerformanceRequest request =
        new PerformanceRequest(
            "공연",
            "공연장",
            performance.getPerformanceAt(),
            50_000,
            29,
            now.minusDays(3),
            now.minusDays(2));

    // when
    Throwable thrown =
        catchThrowable(() -> performanceService.update(performance.getId(), request));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PERFORMANCE_ALREADY_STARTED));
  }

  @Test
  @DisplayName("공연 일시가 지난 공연이라도 판매 기간이 유효하지 않으면 판매 기간 오류를 반환한다")
  void rejectsInvalidSalePeriodBeforeStartedCheck() {
    // given
    LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    Performance performance = save("공연", now.minusDays(3), now.minusDays(2), 100);
    PerformanceRequest request =
        new PerformanceRequest(
            "공연",
            "공연장",
            performance.getPerformanceAt(),
            50_000,
            100,
            now.minusDays(3),
            now.minusDays(3));

    // when
    Throwable thrown =
        catchThrowable(() -> performanceService.update(performance.getId(), request));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_SALE_PERIOD));
  }

  @Test
  @DisplayName("없는 공연을 조회하면 공연 없음 예외가 발생한다")
  void throwsNotFoundWhenPerformanceDoesNotExist() {
    // when
    Throwable thrown = catchThrowable(() -> performanceService.getPerformance(Long.MAX_VALUE));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PERFORMANCE_NOT_FOUND));
  }

  @Test
  @DisplayName("없는 공연을 수정하면 공연 없음 예외가 발생한다")
  void throwsNotFoundWhenUpdatingMissingPerformance() {
    // given
    LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    PerformanceRequest request =
        new PerformanceRequest(
            "공연", "공연장", now.plusDays(10), 50_000, 100, now.plusDays(1), now.plusDays(9));

    // when
    Throwable thrown = catchThrowable(() -> performanceService.update(Long.MAX_VALUE, request));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PERFORMANCE_NOT_FOUND));
  }

  @Test
  @DisplayName("없는 공연이면 판매 기간 오류보다 공연 없음 오류를 먼저 반환한다")
  void rejectsMissingPerformanceBeforeSalePeriodCheck() {
    // given
    LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    PerformanceRequest request =
        new PerformanceRequest(
            "공연", "공연장", now.plusDays(10), 50_000, 100, now.plusDays(1), now.plusDays(1));

    // when
    Throwable thrown = catchThrowable(() -> performanceService.update(Long.MAX_VALUE, request));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PERFORMANCE_NOT_FOUND));
  }

  @Test
  @DisplayName("예약 가능한 공연만 조회하면 현재 시각 기준으로 판매 중인 공연을 반환한다")
  void findsAvailablePerformancesBasedOnCurrentTime() {
    // given
    LocalDateTime now = LocalDateTime.now(clock);
    Performance onSale = save("판매 중", now.minusDays(1), now.plusDays(1), 100);
    save("판매 예정", now.plusDays(1), now.plusDays(2), 50);

    // when
    List<PerformanceDetail> details = performanceService.getPerformances(true);

    // then
    assertThat(details)
        .extracting(
            detail -> detail.performance().getId(), detail -> detail.stock().getRemainingQuantity())
        .containsExactly(tuple(onSale.getId(), 100));
  }

  private Performance save(
      String title, LocalDateTime saleStartAt, LocalDateTime saleEndAt, int totalQuantity) {
    Performance performance =
        performanceRepository.save(
            Performance.create(
                title,
                "공연장",
                saleEndAt.plusDays(1),
                50_000,
                new SalePeriod(saleStartAt, saleEndAt),
                saleStartAt.minusDays(1)));
    stockRepository.save(PerformanceStock.create(performance.getId(), totalQuantity));
    return performance;
  }

  private void reserve(Performance performance, int reservedQuantity) {
    PerformanceStock stock = stockRepository.findById(performance.getId()).orElseThrow();
    ReflectionTestUtils.setField(stock, "reservedQuantity", reservedQuantity);
    stockRepository.save(stock);
  }
}
