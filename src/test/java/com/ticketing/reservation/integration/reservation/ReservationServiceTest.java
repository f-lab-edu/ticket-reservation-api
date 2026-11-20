package com.ticketing.reservation.integration.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import com.ticketing.reservation.global.web.IfMatch;
import com.ticketing.reservation.integration.TestcontainersConfiguration;
import com.ticketing.reservation.member.domain.Member;
import com.ticketing.reservation.member.repository.MemberRepository;
import com.ticketing.reservation.performance.domain.Performance;
import com.ticketing.reservation.performance.domain.PerformanceInfo;
import com.ticketing.reservation.performance.domain.PerformanceStock;
import com.ticketing.reservation.performance.repository.PerformanceRepository;
import com.ticketing.reservation.performance.repository.PerformanceStockRepository;
import com.ticketing.reservation.performance.service.PerformanceService;
import com.ticketing.reservation.reservation.domain.Reservation;
import com.ticketing.reservation.reservation.domain.ReservationStatus;
import com.ticketing.reservation.reservation.repository.ReservationRepository;
import com.ticketing.reservation.reservation.service.ReservationService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReservationServiceTest {

  private static final Duration LOCK_HOLD_LIMIT = Duration.ofSeconds(10);

  @Autowired private ReservationService reservationService;
  @Autowired private PerformanceService performanceService;
  @Autowired private ReservationRepository reservationRepository;
  @Autowired private PerformanceRepository performanceRepository;
  @Autowired private PerformanceStockRepository stockRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private Clock clock;
  @Autowired private PlatformTransactionManager transactionManager;

  private Long memberId;
  private LocalDateTime now;

  @BeforeEach
  void setUp() {
    memberId = memberRepository.save(Member.create("member@example.com", "{bcrypt}hash")).getId();
    now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
  }

  @AfterEach
  void tearDown() {
    reservationRepository.deleteAllInBatch();
    stockRepository.deleteAllInBatch();
    performanceRepository.deleteAllInBatch();
    memberRepository.deleteAllInBatch();
  }

  @Test
  @DisplayName("예약하면 결제 대기 예약을 저장하고 재고를 차감한다")
  void savesReservationAndDecreasesStock() {
    // given
    Performance performance = save(now.minusDays(1), now.plusDays(1), 100, 0);

    // when
    Reservation reservation = reservationService.reserve(memberId, performance.getId(), 3);

    // then
    Reservation saved = reservationRepository.findById(reservation.getId()).orElseThrow();
    assertThat(saved.getMemberId()).isEqualTo(memberId);
    assertThat(saved.getPerformanceId()).isEqualTo(performance.getId());
    assertThat(saved.getQuantity()).isEqualTo(3);
    assertThat(saved.getUnitPrice()).isEqualTo(50_000);
    assertThat(saved.getTotalPrice()).isEqualTo(150_000L);
    assertThat(saved.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
    assertThat(saved.getReservedAt()).isEqualTo(reservation.getReservedAt());
    assertThat(saved.getPaymentDeadlineAt()).isEqualTo(reservation.getPaymentDeadlineAt());
    assertThat(remainingOf(performance)).isEqualTo(97);
  }

  @Test
  @DisplayName("잔여 수량보다 많이 예약하면 거절하고 예약을 저장하지 않는다")
  void rejectsOverRemaining() {
    // given
    Performance performance = save(now.minusDays(1), now.plusDays(1), 100, 98);

    // when
    Throwable thrown =
        catchThrowable(() -> reservationService.reserve(memberId, performance.getId(), 3));

    // then
    assertThat(thrown).satisfies(hasErrorCode(ErrorCode.INSUFFICIENT_STOCK));
    assertThat(remainingOf(performance)).isEqualTo(2);
    assertThat(reservationRepository.count()).isZero();
  }

  @Test
  @DisplayName("판매 기간이 아니면서 재고도 부족하면 판매 기간 오류를 먼저 반환한다")
  void rejectsNotOnSaleBeforeStockCheck() {
    // given
    Performance performance = save(now.plusDays(1), now.plusDays(2), 100, 100);

    // when
    Throwable thrown =
        catchThrowable(() -> reservationService.reserve(memberId, performance.getId(), 1));

    // then
    assertThat(thrown).satisfies(hasErrorCode(ErrorCode.NOT_ON_SALE));
    assertThat(remainingOf(performance)).isZero();
    assertThat(reservationRepository.count()).isZero();
  }

  @Test
  @DisplayName("재고 잠금을 제한 시간 안에 얻지 못하면 재고 혼잡 오류를 반환한다")
  void rejectsWhenStockLockTimesOut() throws Exception {
    // given
    Performance performance = save(now.minusDays(1), now.plusDays(1), 100, 0);
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);

    try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
      Future<?> lockHolder =
          executor.submit(
              () ->
                  transaction.executeWithoutResult(
                      status -> {
                        stockRepository.findByIdForUpdate(performance.getId());
                        locked.countDown();
                        awaitRelease(release);
                      }));
      locked.await();

      // when
      Throwable thrown =
          catchThrowable(() -> reservationService.reserve(memberId, performance.getId(), 1));
      release.countDown();
      lockHolder.get();

      // then
      assertThat(thrown).satisfies(hasErrorCode(ErrorCode.STOCK_BUSY));
      assertThat(remainingOf(performance)).isEqualTo(100);
      assertThat(reservationRepository.count()).isZero();
    }
  }

  @Test
  @DisplayName("없는 공연은 예약할 수 없다")
  void rejectsMissingPerformance() {
    // when
    Throwable thrown =
        catchThrowable(() -> reservationService.reserve(memberId, Long.MAX_VALUE, 1));

    // then
    assertThat(thrown).satisfies(hasErrorCode(ErrorCode.PERFORMANCE_NOT_FOUND));
  }

  @Test
  @DisplayName("동시에 예약해도 총 판매 수량을 초과하지 않는다")
  void doesNotOversellUnderConcurrentReservations() throws Exception {
    // given
    Performance performance = save(now.minusDays(1), now.plusDays(1), 10, 0);

    // when
    List<Boolean> results =
        runConcurrently(
            50,
            () -> succeeded(() -> reservationService.reserve(memberId, performance.getId(), 1)));

    // then
    assertThat(results).filteredOn(Boolean::booleanValue).hasSize(10);
    assertThat(remainingOf(performance)).isZero();
    assertThat(reservationRepository.count()).isEqualTo(10);
  }

  @Test
  @DisplayName("총 판매 수량 수정과 예약이 동시에 일어나도 예약 수량이 총 판매 수량을 넘지 않는다")
  void keepsStockConsistentWhenUpdateAndReservationRace() throws Exception {
    // given
    Performance performance = save(now.minusDays(1), now.plusDays(1), 100, 80);
    PerformanceInfo info =
        new PerformanceInfo(
            "공연",
            "공연장",
            performance.getPerformanceAt(),
            50_000,
            performance.getSalePeriod().saleStartAt(),
            performance.getSalePeriod().saleEndAt());

    // when
    List<Boolean> results =
        runConcurrently(
            List.of(
                () ->
                    succeeded(
                        () ->
                            performanceService.update(
                                performance.getId(),
                                info,
                                80,
                                IfMatch.parse(IfMatch.eTagOf(performance.getVersion())))),
                () ->
                    succeeded(
                        () -> reservationService.reserve(memberId, performance.getId(), 10))));

    // then
    assertThat(results).containsExactlyInAnyOrder(true, false);
    PerformanceStock stock = stockRepository.findById(performance.getId()).orElseThrow();
    assertThat(stock.getRemainingQuantity()).isGreaterThanOrEqualTo(0);
    boolean updateSucceeded = results.getFirst();
    if (updateSucceeded) {
      assertThat(stock.getTotalQuantity()).isEqualTo(80);
      assertThat(stock.getRemainingQuantity()).isZero();
    } else {
      assertThat(stock.getTotalQuantity()).isEqualTo(100);
      assertThat(stock.getRemainingQuantity()).isEqualTo(10);
    }
  }

  @Test
  @DisplayName("판매 시작 일시 수정과 첫 예약이 동시에 일어나도 둘 중 하나만 성공한다")
  void allowsOnlyOneOfSaleStartChangeAndFirstReservation() throws Exception {
    // given
    Performance performance = save(now.minusDays(1), now.plusDays(1), 100, 0);
    LocalDateTime delayedSaleStartAt = now.plusHours(1);
    PerformanceInfo info =
        new PerformanceInfo(
            "공연",
            "공연장",
            performance.getPerformanceAt(),
            50_000,
            delayedSaleStartAt,
            performance.getSalePeriod().saleEndAt());

    // when
    List<Boolean> results =
        runConcurrently(
            List.of(
                () ->
                    succeeded(
                        () ->
                            performanceService.update(
                                performance.getId(),
                                info,
                                100,
                                IfMatch.parse(IfMatch.eTagOf(performance.getVersion())))),
                () ->
                    succeeded(() -> reservationService.reserve(memberId, performance.getId(), 1))));

    // then
    assertThat(results).containsExactlyInAnyOrder(true, false);
    Performance saved = performanceRepository.findById(performance.getId()).orElseThrow();
    boolean updateSucceeded = results.getFirst();
    if (updateSucceeded) {
      assertThat(saved.getSalePeriod().saleStartAt()).isEqualTo(delayedSaleStartAt);
      assertThat(reservationRepository.count()).isZero();
    } else {
      assertThat(saved.getSalePeriod().saleStartAt())
          .isEqualTo(performance.getSalePeriod().saleStartAt());
      assertThat(reservationRepository.count()).isOne();
    }
  }

  private Performance save(
      LocalDateTime saleStartAt, LocalDateTime saleEndAt, int totalQuantity, int reservedQuantity) {
    Performance performance =
        performanceRepository.save(
            Performance.create(
                new PerformanceInfo(
                    "공연", "공연장", saleEndAt.plusDays(1), 50_000, saleStartAt, saleEndAt),
                saleStartAt.minusDays(1)));
    PerformanceStock stock = PerformanceStock.create(performance.getId(), totalQuantity);
    ReflectionTestUtils.setField(stock, "reservedQuantity", reservedQuantity);
    stockRepository.save(stock);
    return performance;
  }

  private int remainingOf(Performance performance) {
    return stockRepository.findById(performance.getId()).orElseThrow().getRemainingQuantity();
  }

  private static Consumer<Throwable> hasErrorCode(ErrorCode errorCode) {
    return thrown ->
        assertThat(thrown)
            .isInstanceOfSatisfying(
                BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
  }

  private static void awaitRelease(CountDownLatch latch) {
    try {
      if (!latch.await(LOCK_HOLD_LIMIT.toMillis(), TimeUnit.MILLISECONDS)) {
        throw new IllegalStateException("재고 락 점유 제한 시간 초과");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("재고 락 점유 스레드가 중단되었습니다", e);
    }
  }

  private static boolean succeeded(Runnable task) {
    try {
      task.run();
      return true;
    } catch (BusinessException e) {
      return false;
    }
  }

  private static <T> List<T> runConcurrently(int threadCount, Callable<T> task) throws Exception {
    return runConcurrently(Collections.nCopies(threadCount, task));
  }

  private static <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
    CountDownLatch ready = new CountDownLatch(tasks.size());
    CountDownLatch start = new CountDownLatch(1);

    try (ExecutorService executor = Executors.newFixedThreadPool(tasks.size())) {
      List<Future<T>> futures = new ArrayList<>();
      for (Callable<T> task : tasks) {
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  return task.call();
                }));
      }
      ready.await();
      start.countDown();

      List<T> results = new ArrayList<>();
      for (Future<T> future : futures) {
        results.add(future.get());
      }
      return results;
    }
  }
}
