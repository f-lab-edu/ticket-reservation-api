package com.ticketing.reservation.integration.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import com.ticketing.reservation.global.web.IfMatch;
import com.ticketing.reservation.integration.TestcontainersConfiguration;
import com.ticketing.reservation.performance.domain.Performance;
import com.ticketing.reservation.performance.domain.PerformanceDetail;
import com.ticketing.reservation.performance.domain.PerformanceInfo;
import com.ticketing.reservation.performance.domain.PerformanceStock;
import com.ticketing.reservation.performance.domain.SalePeriod;
import com.ticketing.reservation.performance.repository.PerformanceRepository;
import com.ticketing.reservation.performance.repository.PerformanceStockRepository;
import com.ticketing.reservation.performance.service.PerformanceService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PerformanceServiceTest {

  private static final int RESERVED_QUANTITY = 30;

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
    PerformanceInfo info =
        new PerformanceInfo("공연", "공연장", performanceAt, 50_000, saleStartAt, saleEndAt);

    // when
    PerformanceDetail detail = performanceService.register(info, 100);

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
    UpdateRequest request =
        new UpdateRequest(
            new PerformanceInfo("새 공연", "새 공연장", performanceAt, 60_000, saleStartAt, saleEndAt),
            120);

    // when
    performanceService.update(
        performance.getId(),
        request.info(),
        request.totalQuantity(),
        ifMatch(performance.getVersion()));

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
    UpdateRequest request = requestFor(performance).title("새 공연").totalQuantity(29).build();

    // when
    Throwable thrown =
        catchThrowable(
            () ->
                performanceService.update(
                    performance.getId(),
                    request.info(),
                    request.totalQuantity(),
                    ifMatch(performance.getVersion())));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOTAL_QUANTITY_BELOW_RESERVED));
    Performance saved = performanceRepository.findById(performance.getId()).orElseThrow();
    assertThat(saved.getTitle()).isEqualTo("공연");
  }

  @ParameterizedTest(name = "{0}")
  @DisplayName("여러 오류가 함께 해당하면 우선순위가 높은 오류를 반환한다")
  @MethodSource("conflictingErrors")
  void returnsHigherPriorityErrorWhenErrorsConflict(
      String situation,
      PerformanceState state,
      int reservedQuantity,
      UnaryOperator<PerformanceRequestBuilder> requestModifier,
      ErrorCode expected) {
    // given
    LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    Performance performance =
        state == PerformanceState.STARTED
            ? save("공연", now.minusDays(3), now.minusDays(2), 100)
            : save("공연", now.plusDays(1), now.plusDays(10), 100);
    reserve(performance, reservedQuantity);
    UpdateRequest request = requestModifier.apply(requestFor(performance)).build();

    // when
    Throwable thrown =
        catchThrowable(
            () ->
                performanceService.update(
                    performance.getId(),
                    request.info(),
                    request.totalQuantity(),
                    ifMatch(performance.getVersion())));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
  }

  static Stream<Arguments> conflictingErrors() {
    return Stream.of(
        arguments(
            "시작한 공연 + 예약보다 적은 총 수량 → 공연 시작 오류",
            PerformanceState.STARTED,
            RESERVED_QUANTITY,
            modifyRequest(request -> request.totalQuantity(RESERVED_QUANTITY - 1)),
            ErrorCode.PERFORMANCE_ALREADY_STARTED),
        arguments(
            "예약 있는 공연의 일정 변경 + 예약보다 적은 총 수량 → 일정 변경 불가 오류",
            PerformanceState.NOT_STARTED,
            RESERVED_QUANTITY,
            modifyRequest(
                request -> request.delayPerformanceAt().totalQuantity(RESERVED_QUANTITY - 1)),
            ErrorCode.SCHEDULE_CHANGE_NOT_ALLOWED),
        arguments(
            "잘못된 판매 기간 + 예약보다 적은 총 수량 → 판매 기간 오류",
            PerformanceState.NOT_STARTED,
            RESERVED_QUANTITY,
            modifyRequest(
                request -> request.invalidSalePeriod().totalQuantity(RESERVED_QUANTITY - 1)),
            ErrorCode.INVALID_SALE_PERIOD));
  }

  private static UnaryOperator<PerformanceRequestBuilder> modifyRequest(
      UnaryOperator<PerformanceRequestBuilder> modifier) {
    return modifier;
  }

  private enum PerformanceState {
    STARTED,
    NOT_STARTED
  }

  @Test
  @DisplayName("총 판매 수량만 수정해도 저장된 버전이 1 올라가고 응답의 버전과 같다")
  void increasesVersionWhenOnlyTotalQuantityChanges() {
    // given
    LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    Performance performance = save("공연", now.plusDays(1), now.plusDays(10), 100);
    long initialVersion = performance.getVersion();
    UpdateRequest request = requestFor(performance).totalQuantity(120).build();

    // when
    PerformanceDetail detail =
        performanceService.update(
            performance.getId(), request.info(), request.totalQuantity(), ifMatch(initialVersion));

    // then
    long savedVersion =
        performanceRepository.findById(performance.getId()).orElseThrow().getVersion();
    assertThat(savedVersion).isEqualTo(initialVersion + 1);
    assertThat(detail.performance().getVersion()).isEqualTo(savedVersion);
  }

  @Test
  @DisplayName("같은 버전으로 보낸 수정이 먼저 반영되면 그 버전으로 보낸 다음 수정은 거절하고 공연 정보를 바꾸지 않는다")
  void rejectsUpdateWithStaleVersion() {
    // given
    LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    Performance performance = save("공연", now.plusDays(1), now.plusDays(10), 100);
    long initialVersion = performance.getVersion();
    UpdateRequest firstUpdate = requestFor(performance).title("먼저 수정").build();
    performanceService.update(
        performance.getId(),
        firstUpdate.info(),
        firstUpdate.totalQuantity(),
        ifMatch(initialVersion));

    UpdateRequest secondUpdate = requestFor(performance).title("나중 수정").build();

    // when
    Throwable thrown =
        catchThrowable(
            () ->
                performanceService.update(
                    performance.getId(),
                    secondUpdate.info(),
                    secondUpdate.totalQuantity(),
                    ifMatch(initialVersion)));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PERFORMANCE_MODIFIED));
    Performance saved = performanceRepository.findById(performance.getId()).orElseThrow();
    assertThat(saved.getTitle()).isEqualTo("먼저 수정");
    assertThat(saved.getVersion()).isEqualTo(initialVersion + 1);
  }

  @Test
  @DisplayName("먼저 반영된 수정 때문에 버전이 맞지 않으면 일정 변경 불가 오류보다 수정 충돌 오류를 먼저 반환한다")
  void rejectsStaleVersionBeforeScheduleChangeCheck() {
    // given
    LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    Performance performance = save("공연", now.plusDays(1), now.plusDays(10), 100);
    reserve(performance, 30);
    long initialVersion = performance.getVersion();
    UpdateRequest firstUpdate = requestFor(performance).title("먼저 수정").build();
    performanceService.update(
        performance.getId(),
        firstUpdate.info(),
        firstUpdate.totalQuantity(),
        ifMatch(initialVersion));
    UpdateRequest scheduleChange = requestFor(performance).delayPerformanceAt().build();

    // when
    Throwable thrown =
        catchThrowable(
            () ->
                performanceService.update(
                    performance.getId(),
                    scheduleChange.info(),
                    scheduleChange.totalQuantity(),
                    ifMatch(initialVersion)));

    // then
    assertThat(thrown)
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PERFORMANCE_MODIFIED));
  }

  @Test
  @DisplayName("같은 버전으로 보낸 수정이 동시에 들어오면 하나만 반영하고 나머지는 거절한다")
  void allowsOnlyOneOfConcurrentUpdatesWithSameVersion() throws Exception {
    // given
    LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    Performance performance = save("공연", now.plusDays(1), now.plusDays(10), 100);
    long initialVersion = performance.getVersion();
    int threadCount = 5;
    CountDownLatch ready = new CountDownLatch(threadCount);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<ErrorCode>> futures = new ArrayList<>();

    // when
    try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
      for (int i = 0; i < threadCount; i++) {
        UpdateRequest request =
            requestFor(performance).title("수정 " + i).totalQuantity(100 + i).build();
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  try {
                    performanceService.update(
                        performance.getId(),
                        request.info(),
                        request.totalQuantity(),
                        ifMatch(initialVersion));
                    return null;
                  } catch (BusinessException e) {
                    return e.getErrorCode();
                  }
                }));
      }
      ready.await();
      start.countDown();
    }

    // then
    List<ErrorCode> results = new ArrayList<>();
    for (Future<ErrorCode> future : futures) {
      results.add(future.get());
    }
    assertThat(results).filteredOn(Objects::isNull).hasSize(1);
    assertThat(results).filteredOn(Objects::nonNull).containsOnly(ErrorCode.PERFORMANCE_MODIFIED);
    assertThat(performanceRepository.findById(performance.getId()).orElseThrow().getVersion())
        .isEqualTo(initialVersion + 1);
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
  @DisplayName("없는 공연의 잔여 수량을 조회하면 공연 없음 예외가 발생한다")
  void throwsNotFoundWhenGettingStockOfMissingPerformance() {
    // when
    Throwable thrown = catchThrowable(() -> performanceService.getStock(Long.MAX_VALUE));

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
    UpdateRequest request =
        new UpdateRequest(
            new PerformanceInfo(
                "공연", "공연장", now.plusDays(10), 50_000, now.plusDays(1), now.plusDays(9)),
            100);

    // when
    Throwable thrown =
        catchThrowable(
            () ->
                performanceService.update(
                    Long.MAX_VALUE, request.info(), request.totalQuantity(), ifMatch(0)));

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
    UpdateRequest request =
        new UpdateRequest(
            new PerformanceInfo(
                "공연", "공연장", now.plusDays(10), 50_000, now.plusDays(1), now.plusDays(1)),
            100);

    // when
    Throwable thrown =
        catchThrowable(
            () ->
                performanceService.update(
                    Long.MAX_VALUE, request.info(), request.totalQuantity(), ifMatch(0)));

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
                new PerformanceInfo(
                    title, "공연장", saleEndAt.plusDays(1), 50_000, saleStartAt, saleEndAt),
                saleStartAt.minusDays(1)));
    stockRepository.save(PerformanceStock.create(performance.getId(), totalQuantity));
    return performance;
  }

  private static IfMatch ifMatch(long version) {
    return IfMatch.parse(IfMatch.eTagOf(version));
  }

  private PerformanceRequestBuilder requestFor(Performance performance) {
    int totalQuantity =
        stockRepository.findById(performance.getId()).orElseThrow().getTotalQuantity();
    return new PerformanceRequestBuilder(performance, totalQuantity);
  }

  private static final class PerformanceRequestBuilder {

    private String title;
    private final String venue;
    private LocalDateTime performanceAt;
    private final int price;
    private int totalQuantity;
    private final LocalDateTime saleStartAt;
    private LocalDateTime saleEndAt;

    private PerformanceRequestBuilder(Performance performance, int totalQuantity) {
      this.title = performance.getTitle();
      this.venue = performance.getVenue();
      this.performanceAt = performance.getPerformanceAt();
      this.price = performance.getPrice();
      this.totalQuantity = totalQuantity;
      this.saleStartAt = performance.getSalePeriod().saleStartAt();
      this.saleEndAt = performance.getSalePeriod().saleEndAt();
    }

    PerformanceRequestBuilder title(String title) {
      this.title = title;
      return this;
    }

    PerformanceRequestBuilder totalQuantity(int totalQuantity) {
      this.totalQuantity = totalQuantity;
      return this;
    }

    PerformanceRequestBuilder delayPerformanceAt() {
      this.performanceAt = performanceAt.plusDays(1);
      return this;
    }

    PerformanceRequestBuilder invalidSalePeriod() {
      this.saleEndAt = saleStartAt;
      return this;
    }

    UpdateRequest build() {
      return new UpdateRequest(
          new PerformanceInfo(title, venue, performanceAt, price, saleStartAt, saleEndAt),
          totalQuantity);
    }
  }

  private record UpdateRequest(PerformanceInfo info, int totalQuantity) {}

  private void reserve(Performance performance, int reservedQuantity) {
    PerformanceStock stock = stockRepository.findById(performance.getId()).orElseThrow();
    ReflectionTestUtils.setField(stock, "reservedQuantity", reservedQuantity);
    stockRepository.save(stock);
  }
}
