package com.ticketing.reservation.integration.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.params.provider.Arguments.arguments;

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
import com.ticketing.reservation.performance.service.PerformanceStockLocker;
import com.ticketing.reservation.reservation.domain.Reservation;
import com.ticketing.reservation.reservation.domain.ReservationDetail;
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
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
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
  private static final Duration WAIT_LIMIT = Duration.ofSeconds(10);

  @Autowired private ReservationService reservationService;
  @Autowired private PerformanceService performanceService;
  @Autowired private ReservationRepository reservationRepository;
  @Autowired private PerformanceRepository performanceRepository;
  @Autowired private PerformanceStockRepository stockRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private PerformanceStockLocker stockLocker;
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
      assertThat(locked.await(WAIT_LIMIT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();

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
  @DisplayName("예약으로 재고가 변경된 후에도 기존 ETag로 공연 정보를 수정한다")
  void updatesWithExistingETagAfterReservation() {
    // given
    Performance performance = save(now.minusDays(1), now.plusDays(1), 100, 0);
    IfMatch ifMatch = IfMatch.parse(IfMatch.eTagOf(performance.getVersion()));
    reservationService.reserve(memberId, performance.getId(), 3);

    // when
    performanceService.update(
        performance.getId(),
        infoOf(performance, "새 공연", performance.getSalePeriod().saleStartAt()),
        50,
        ifMatch);

    // then
    Performance saved = performanceRepository.findById(performance.getId()).orElseThrow();
    assertThat(saved.getTitle()).isEqualTo("새 공연");
    assertThat(saved.getVersion()).isEqualTo(performance.getVersion() + 1);
    assertThat(remainingOf(performance)).isEqualTo(47);
  }

  @ParameterizedTest(name = "{0}")
  @DisplayName("예약으로 재고가 변경된 후 기존 ETag로 수정하면 비즈니스 규칙 오류를 반환한다")
  @MethodSource("ruleViolationsAfterReservation")
  void returnsBusinessRuleErrorWithExistingETagAfterReservation(
      String situation, boolean delaySaleStart, int totalQuantity, ErrorCode expected) {
    // given
    Performance performance = save(now.minusDays(1), now.plusDays(1), 100, 0);
    IfMatch ifMatch = IfMatch.parse(IfMatch.eTagOf(performance.getVersion()));
    reservationService.reserve(memberId, performance.getId(), 3);
    LocalDateTime saleStartAt =
        delaySaleStart ? now.plusHours(1) : performance.getSalePeriod().saleStartAt();

    // when
    Throwable thrown =
        catchThrowable(
            () ->
                performanceService.update(
                    performance.getId(),
                    infoOf(performance, "공연", saleStartAt),
                    totalQuantity,
                    ifMatch));

    // then
    assertThat(thrown).satisfies(hasErrorCode(expected));
    assertThat(performanceRepository.findById(performance.getId()).orElseThrow().getVersion())
        .isEqualTo(performance.getVersion());
    assertThat(stockRepository.findById(performance.getId()).orElseThrow().getTotalQuantity())
        .isEqualTo(100);
  }

  static Stream<Arguments> ruleViolationsAfterReservation() {
    return Stream.of(
        arguments("예약보다 적은 총 수량 → 예약 수량 오류", false, 2, ErrorCode.TOTAL_QUANTITY_BELOW_RESERVED),
        arguments("판매 시작 일시 변경 → 일정 변경 불가 오류", true, 100, ErrorCode.SCHEDULE_CHANGE_NOT_ALLOWED));
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

  @Test
  @DisplayName("내 예약 목록을 조회하면 본인 예약만 최신순으로 공연 정보와 함께 반환한다")
  void returnsOnlyMyReservationsLatestFirst() {
    // given
    Performance first = save(now.minusDays(1), now.plusDays(1), 100, 0);
    Performance second = save(now.minusDays(1), now.plusDays(2), 100, 0);
    Reservation older = reservationService.reserve(memberId, first.getId(), 1);
    Reservation newer = reservationService.reserve(memberId, second.getId(), 2);
    reservationService.reserve(otherMemberId(), first.getId(), 3);

    // when
    List<ReservationDetail> details = reservationService.getMyReservations(memberId, Set.of());

    // then
    assertThat(details)
        .extracting(d -> d.reservation().getId(), d -> d.performance().getId())
        .containsExactly(tuple(newer.getId(), second.getId()), tuple(older.getId(), first.getId()));
  }

  @Test
  @DisplayName("예약이 없으면 빈 목록을 반환한다")
  void returnsEmptyListWithoutReservations() {
    // when
    List<ReservationDetail> details = reservationService.getMyReservations(memberId, Set.of());

    // then
    assertThat(details).isEmpty();
  }

  @Test
  @DisplayName("상태를 지정하지 않으면 모든 상태의 예약을 조회한다")
  void includesExpiredWithoutStatusFilter() {
    // given
    Performance performance = save(now.minusDays(1), now.plusDays(1), 100, 0);
    Reservation expired = saveExpired(performance);
    Reservation pending = reservationService.reserve(memberId, performance.getId(), 1);

    // when
    List<ReservationDetail> details = reservationService.getMyReservations(memberId, Set.of());

    // then
    assertThat(details)
        .extracting(d -> d.reservation().getId(), ReservationDetail::status)
        .containsExactly(
            tuple(pending.getId(), ReservationStatus.PENDING_PAYMENT),
            tuple(expired.getId(), ReservationStatus.EXPIRED));
  }

  @ParameterizedTest(name = "{0}")
  @DisplayName("상태를 지정하면 조회 시점 상태가 일치하는 예약만 반환한다")
  @MethodSource("statusFilters")
  void filtersByStatus(Set<ReservationStatus> statuses, List<ReservationStatus> expected) {
    // given
    Performance performance = save(now.minusDays(1), now.plusDays(1), 100, 0);
    saveExpired(performance);
    reservationService.reserve(memberId, performance.getId(), 1);

    // when
    List<ReservationDetail> details = reservationService.getMyReservations(memberId, statuses);

    // then
    assertThat(details).extracting(ReservationDetail::status).containsExactlyElementsOf(expected);
  }

  static Stream<Arguments> statusFilters() {
    return Stream.of(
        arguments(
            Set.of(ReservationStatus.PENDING_PAYMENT), List.of(ReservationStatus.PENDING_PAYMENT)),
        arguments(Set.of(ReservationStatus.EXPIRED), List.of(ReservationStatus.EXPIRED)),
        arguments(
            Set.of(ReservationStatus.PENDING_PAYMENT, ReservationStatus.EXPIRED),
            List.of(ReservationStatus.PENDING_PAYMENT, ReservationStatus.EXPIRED)));
  }

  @ParameterizedTest(name = "제한 시각 {0}ns → {1}")
  @DisplayName("결제 제한 시각 경계에서 상태를 걸러 조회한다")
  @CsvSource({"-1, PENDING_PAYMENT", "0, EXPIRED"})
  void filtersByStatusAtDeadlineBoundary(long nanosFromDeadline, ReservationStatus status) {
    // given
    Performance performance = save(now.minusDays(1), now.plusDays(1), 100, 0);
    Reservation reservation =
        reservationRepository.save(Reservation.create(memberId, performance, 1, now));
    LocalDateTime queriedAt = reservation.getPaymentDeadlineAt().plusNanos(nanosFromDeadline);
    Clock fixed = Clock.fixed(queriedAt.atZone(clock.getZone()).toInstant(), clock.getZone());
    ReservationService service =
        new ReservationService(reservationRepository, performanceRepository, stockLocker, fixed);

    // when
    List<ReservationDetail> details = service.getMyReservations(memberId, Set.of(status));

    // then
    assertThat(details).extracting(ReservationDetail::status).containsExactly(status);
  }

  @Test
  @DisplayName("결제 제한 시각이 지난 예약을 상세 조회하면 만료 상태로 반환한다")
  void showsExpiredInDetail() {
    // given
    Performance performance = save(now.minusDays(1), now.plusDays(1), 100, 0);
    Reservation expired = saveExpired(performance);

    // when
    ReservationDetail detail = reservationService.getMyReservation(memberId, expired.getId());

    // then
    assertThat(detail.status()).isEqualTo(ReservationStatus.EXPIRED);
  }

  @Test
  @DisplayName("내 예약을 상세 조회하면 공연 정보와 함께 반환한다")
  void returnsMyReservationDetail() {
    // given
    Performance performance = save(now.minusDays(1), now.plusDays(1), 100, 0);
    Reservation reservation = reservationService.reserve(memberId, performance.getId(), 2);

    // when
    ReservationDetail detail = reservationService.getMyReservation(memberId, reservation.getId());

    // then
    assertThat(detail.reservation().getId()).isEqualTo(reservation.getId());
    assertThat(detail.performance().getId()).isEqualTo(performance.getId());
    assertThat(detail.status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
  }

  @Test
  @DisplayName("다른 회원의 예약은 찾을 수 없다")
  void rejectsOtherMembersReservation() {
    // given
    Performance performance = save(now.minusDays(1), now.plusDays(1), 100, 0);
    Reservation others = reservationService.reserve(otherMemberId(), performance.getId(), 1);

    // when
    Throwable thrown =
        catchThrowable(() -> reservationService.getMyReservation(memberId, others.getId()));

    // then
    assertThat(thrown).satisfies(hasErrorCode(ErrorCode.RESERVATION_NOT_FOUND));
  }

  @Test
  @DisplayName("없는 예약은 찾을 수 없다")
  void rejectsMissingReservation() {
    // when
    Throwable thrown =
        catchThrowable(() -> reservationService.getMyReservation(memberId, Long.MAX_VALUE));

    // then
    assertThat(thrown).satisfies(hasErrorCode(ErrorCode.RESERVATION_NOT_FOUND));
  }

  private Reservation saveExpired(Performance performance) {
    return reservationRepository.save(
        Reservation.create(memberId, performance, 1, now.minusMinutes(11)));
  }

  private Long otherMemberId() {
    return memberRepository.save(Member.create("other@example.com", "{bcrypt}hash")).getId();
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

  private static PerformanceInfo infoOf(
      Performance performance, String title, LocalDateTime saleStartAt) {
    return new PerformanceInfo(
        title,
        performance.getVenue(),
        performance.getPerformanceAt(),
        performance.getPrice(),
        saleStartAt,
        performance.getSalePeriod().saleEndAt());
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
    CyclicBarrier start = new CyclicBarrier(tasks.size());

    try (ExecutorService executor = Executors.newFixedThreadPool(tasks.size())) {
      List<Future<T>> futures = new ArrayList<>();
      for (Callable<T> task : tasks) {
        futures.add(
            executor.submit(
                () -> {
                  start.await(WAIT_LIMIT.toMillis(), TimeUnit.MILLISECONDS);
                  return task.call();
                }));
      }

      List<T> results = new ArrayList<>();
      for (Future<T> future : futures) {
        results.add(future.get(WAIT_LIMIT.toMillis(), TimeUnit.MILLISECONDS));
      }
      return results;
    }
  }
}
