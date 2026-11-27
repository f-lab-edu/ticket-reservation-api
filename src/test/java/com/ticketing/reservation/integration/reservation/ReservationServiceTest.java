package com.ticketing.reservation.integration.reservation;

import static com.ticketing.reservation.integration.ConcurrencyTestSupport.runBlockedByOtherTransactionLock;
import static com.ticketing.reservation.integration.ConcurrencyTestSupport.runConcurrently;
import static com.ticketing.reservation.integration.ConcurrencyTestSupport.runWhileOtherTransactionHoldsLock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertTimeout;
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
import com.ticketing.reservation.performance.service.OverdueReservationExpirer;
import com.ticketing.reservation.performance.service.PerformanceService;
import com.ticketing.reservation.performance.service.PerformanceStockLocker;
import com.ticketing.reservation.reservation.domain.Reservation;
import com.ticketing.reservation.reservation.domain.ReservationDetail;
import com.ticketing.reservation.reservation.domain.ReservationStatus;
import com.ticketing.reservation.reservation.repository.ReservationRepository;
import com.ticketing.reservation.reservation.service.ReservationExpiryScheduler;
import com.ticketing.reservation.reservation.service.ReservationService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReservationServiceTest {

  @Autowired private ReservationService reservationService;
  @Autowired private PerformanceService performanceService;
  @Autowired private ReservationRepository reservationRepository;
  @Autowired private PerformanceRepository performanceRepository;
  @Autowired private PerformanceStockRepository stockRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private PerformanceStockLocker stockLocker;
  @Autowired private OverdueReservationExpirer overdueReservationExpirer;
  @Autowired private ReservationExpiryScheduler expiryScheduler;
  @Autowired private Clock clock;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbcTemplate;

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
  @DisplayName("예약 신청 시 재고 락을 제한 시간 안에 획득하지 못하면 재고 혼잡 오류를 반환한다")
  void rejectsWhenStockLockTimesOut() throws Exception {
    // given
    Performance performance = save(now.minusDays(1), now.plusDays(1), 100, 0);

    // when
    Throwable thrown =
        runWhileOtherTransactionHoldsLock(
            transactionManager,
            () -> stockRepository.findByIdForUpdate(performance.getId()),
            () ->
                catchThrowable(() -> reservationService.reserve(memberId, performance.getId(), 1)));

    // then
    assertThat(thrown).satisfies(hasErrorCode(ErrorCode.STOCK_BUSY));
    assertThat(remainingOf(performance)).isEqualTo(100);
    assertThat(reservationRepository.count()).isZero();
  }

  @Test
  @DisplayName("공연 수정 시 재고 락을 제한 시간 안에 획득하지 못하면 재고 혼잡 오류를 반환한다")
  void rejectsUpdateWhenStockLockTimesOut() throws Exception {
    // given
    Performance performance = save(now.minusDays(1), now.plusDays(1), 100, 0);

    // when
    Throwable thrown =
        runWhileOtherTransactionHoldsLock(
            transactionManager,
            () -> stockRepository.findByIdForUpdate(performance.getId()),
            () ->
                catchThrowable(
                    () ->
                        performanceService.update(
                            performance.getId(),
                            infoOf(
                                performance, "수정된 공연", performance.getSalePeriod().saleStartAt()),
                            50,
                            IfMatch.parse(IfMatch.eTagOf(performance.getVersion())))));

    // then
    assertThat(thrown).satisfies(hasErrorCode(ErrorCode.STOCK_BUSY));
    assertThat(performanceRepository.findById(performance.getId()).orElseThrow().getVersion())
        .isEqualTo(performance.getVersion());
    assertThat(stockRepository.findById(performance.getId()).orElseThrow().getTotalQuantity())
        .isEqualTo(100);
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
  @DisplayName("같은 공연에 처리 중인 예약이 있으면 다음 예약은 기다렸다가 성공한다")
  void waitsForStockLockOnSamePerformance() throws Exception {
    // given
    Performance performance = save(now.minusDays(1), now.plusDays(1), 100, 0);

    // when
    runBlockedByOtherTransactionLock(
        transactionManager,
        jdbcTemplate,
        () -> reservationService.reserve(memberId, performance.getId(), 1),
        () -> reservationService.reserve(memberId, performance.getId(), 1));

    // then
    assertThat(remainingOf(performance)).isEqualTo(98);
    assertThat(reservationRepository.count()).isEqualTo(2);
  }

  @Test
  @DisplayName("다른 공연의 예약이 처리 중이어도 예약할 수 있다")
  void reservesDifferentPerformancesConcurrently() throws Exception {
    // given
    Performance first = save(now.minusDays(1), now.plusDays(1), 100, 0);
    Performance second = save(now.minusDays(1), now.plusDays(1), 100, 0);

    // when
    runWhileOtherTransactionHoldsLock(
        transactionManager,
        () -> reservationService.reserve(memberId, first.getId(), 1),
        () -> reservationService.reserve(memberId, second.getId(), 1));

    // then
    assertThat(reservationRepository.count()).isEqualTo(2);
  }

  @Test
  @DisplayName("다른 공연의 수정이 처리 중이어도 예약할 수 있다")
  void updatesAndReservesDifferentPerformancesConcurrently() throws Exception {
    // given
    Performance updated = save(now.minusDays(1), now.plusDays(1), 100, 0);
    Performance reserved = save(now.minusDays(1), now.plusDays(1), 100, 0);
    saveOverdue(updated, 1);

    // when
    runWhileOtherTransactionHoldsLock(
        transactionManager,
        () -> update(updated),
        () -> reservationService.reserve(memberId, reserved.getId(), 1));

    // then
    assertThat(remainingOf(reserved)).isEqualTo(99);
  }

  @Test
  @DisplayName("다른 공연의 수정이 처리 중이어도 공연을 수정할 수 있다")
  void updatesDifferentPerformancesConcurrently() throws Exception {
    // given
    Performance first = save(now.minusDays(1), now.plusDays(1), 100, 0);
    Performance second = save(now.minusDays(1), now.plusDays(1), 100, 0);
    Reservation firstOverdue = saveOverdue(first, 1);
    saveOverdue(second, 1);

    // when
    runWhileOtherTransactionHoldsLock(
        transactionManager, () -> update(second), () -> update(first));

    // then
    assertThat(performanceRepository.findById(first.getId()).orElseThrow().getTitle())
        .isEqualTo("수정된 공연");
    assertThat(storedStatusOf(firstOverdue)).isEqualTo(ReservationStatus.EXPIRED);
  }

  @Test
  @DisplayName("다른 공연의 만료 처리가 진행 중이어도 예약할 수 있다")
  void expiresAndReservesDifferentPerformancesConcurrently() throws Exception {
    // given
    Performance expired = save(now.minusDays(1), now.plusDays(1), 100, 0);
    Performance reserved = save(now.minusDays(1), now.plusDays(1), 100, 0);
    saveOverdue(expired, 1);

    // when
    runWhileOtherTransactionHoldsLock(
        transactionManager,
        () -> reservationService.expireOverdue(expired.getId()),
        () -> reservationService.reserve(memberId, reserved.getId(), 1));

    // then
    assertThat(remainingOf(reserved)).isEqualTo(99);
  }

  @Test
  @DisplayName("다른 공연의 만료 처리가 진행 중이어도 공연을 수정할 수 있다")
  void expiresAndUpdatesDifferentPerformancesConcurrently() throws Exception {
    // given
    Performance updated = save(now.minusDays(1), now.plusDays(1), 100, 0);
    Performance expired = save(now.minusDays(1), now.plusDays(1), 100, 0);
    Reservation updatedOverdue = saveOverdue(updated, 1);
    saveOverdue(expired, 1);

    // when
    runWhileOtherTransactionHoldsLock(
        transactionManager,
        () -> reservationService.expireOverdue(expired.getId()),
        () -> update(updated));

    // then
    assertThat(performanceRepository.findById(updated.getId()).orElseThrow().getTitle())
        .isEqualTo("수정된 공연");
    assertThat(storedStatusOf(updatedOverdue)).isEqualTo(ReservationStatus.EXPIRED);
  }

  @Test
  @DisplayName("다른 공연의 만료 처리가 진행 중이어도 만료 처리할 수 있다")
  void expiresDifferentPerformancesConcurrently() throws Exception {
    // given
    Performance first = save(now.minusDays(1), now.plusDays(1), 100, 0);
    Performance second = save(now.minusDays(1), now.plusDays(1), 100, 0);
    Reservation firstOverdue = saveOverdue(first, 1);
    saveOverdue(second, 1);

    // when
    runWhileOtherTransactionHoldsLock(
        transactionManager,
        () -> reservationService.expireOverdue(second.getId()),
        () -> reservationService.expireOverdue(first.getId()));

    // then
    assertThat(storedStatusOf(firstOverdue)).isEqualTo(ReservationStatus.EXPIRED);
  }

  @Test
  @DisplayName("수정 중인 공연을 예약하면 수정이 끝난 재고를 기준으로 판단한다")
  void reservesAgainstStockUpdatedByWaitedUpdate() throws Exception {
    // given
    Performance performance = save(now.minusDays(1), now.plusDays(1), 100, 80);

    // when
    Throwable thrown =
        runBlockedByOtherTransactionLock(
            transactionManager,
            jdbcTemplate,
            () ->
                performanceService.update(
                    performance.getId(),
                    infoOf(performance, "공연", performance.getSalePeriod().saleStartAt()),
                    80,
                    IfMatch.parse(IfMatch.eTagOf(performance.getVersion()))),
            () ->
                catchThrowable(
                    () -> reservationService.reserve(memberId, performance.getId(), 10)));

    // then
    assertThat(thrown).satisfies(hasErrorCode(ErrorCode.INSUFFICIENT_STOCK));
    assertThat(remainingOf(performance)).isZero();
    assertThat(reservationRepository.count()).isZero();
  }

  @Test
  @DisplayName("예약 중인 공연을 수정하면 예약이 끝난 재고를 기준으로 판단한다")
  void updatesAgainstStockReservedByWaitedReservation() throws Exception {
    // given
    Performance performance = save(now.minusDays(1), now.plusDays(1), 100, 80);

    // when
    Throwable thrown =
        runBlockedByOtherTransactionLock(
            transactionManager,
            jdbcTemplate,
            () -> reservationService.reserve(memberId, performance.getId(), 10),
            () ->
                catchThrowable(
                    () ->
                        performanceService.update(
                            performance.getId(),
                            infoOf(performance, "공연", performance.getSalePeriod().saleStartAt()),
                            80,
                            IfMatch.parse(IfMatch.eTagOf(performance.getVersion())))));

    // then
    assertThat(thrown).satisfies(hasErrorCode(ErrorCode.TOTAL_QUANTITY_BELOW_RESERVED));
    PerformanceStock stock = stockRepository.findById(performance.getId()).orElseThrow();
    assertThat(stock.getTotalQuantity()).isEqualTo(100);
    assertThat(stock.getRemainingQuantity()).isEqualTo(10);
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
      String situation, boolean changeSaleStart, int totalQuantity, ErrorCode expected) {
    // given
    Performance performance = save(now.minusDays(1), now.plusDays(1), 100, 0);
    IfMatch ifMatch = IfMatch.parse(IfMatch.eTagOf(performance.getVersion()));
    reservationService.reserve(memberId, performance.getId(), 3);
    LocalDateTime saleStartAt =
        changeSaleStart ? now.plusHours(1) : performance.getSalePeriod().saleStartAt();

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
  @DisplayName("판매 시작 일시 수정이 먼저 처리되면 동시에 요청된 첫 예약은 거절한다")
  void rejectsFirstReservationWaitingForSaleStartChange() throws Exception {
    // given
    Performance performance = save(now.minusDays(1), now.plusDays(1), 100, 0);
    LocalDateTime delayedSaleStartAt = now.plusHours(1);

    // when
    Throwable thrown =
        runBlockedByOtherTransactionLock(
            transactionManager,
            jdbcTemplate,
            () -> updateSaleStartAt(performance, delayedSaleStartAt),
            () ->
                catchThrowable(() -> reservationService.reserve(memberId, performance.getId(), 1)));

    // then
    assertThat(thrown).satisfies(hasErrorCode(ErrorCode.NOT_ON_SALE));
    assertThat(
            performanceRepository
                .findById(performance.getId())
                .orElseThrow()
                .getSalePeriod()
                .saleStartAt())
        .isEqualTo(delayedSaleStartAt);
    assertThat(reservationRepository.count()).isZero();
  }

  @Test
  @DisplayName("첫 예약이 먼저 처리되면 동시에 요청된 판매 시작 일시 수정은 거절한다")
  void rejectsSaleStartChangeWaitingForFirstReservation() throws Exception {
    // given
    Performance performance = save(now.minusDays(1), now.plusDays(1), 100, 0);

    // when
    Throwable thrown =
        runBlockedByOtherTransactionLock(
            transactionManager,
            jdbcTemplate,
            () -> reservationService.reserve(memberId, performance.getId(), 1),
            () -> catchThrowable(() -> updateSaleStartAt(performance, now.plusHours(1))));

    // then
    assertThat(thrown).satisfies(hasErrorCode(ErrorCode.SCHEDULE_CHANGE_NOT_ALLOWED));
    assertThat(
            performanceRepository
                .findById(performance.getId())
                .orElseThrow()
                .getSalePeriod()
                .saleStartAt())
        .isEqualTo(performance.getSalePeriod().saleStartAt());
    assertThat(reservationRepository.count()).isOne();
  }

  @Test
  @DisplayName("결제 제한 시각이 지난 예약 수량은 예약 신청 시 다시 판매한다")
  void resellsExpiredQuantityOnReserve() {
    // given
    Performance performance = save(now.minusDays(1), now.plusDays(1), 1, 0);
    Reservation expired = saveOverdue(performance, 1);

    // when
    Reservation reservation = reservationService.reserve(memberId, performance.getId(), 1);

    // then
    assertThat(storedStatusOf(expired)).isEqualTo(ReservationStatus.EXPIRED);
    assertThat(storedStatusOf(reservation)).isEqualTo(ReservationStatus.PENDING_PAYMENT);
    assertThat(remainingOf(performance)).isZero();
  }

  @Test
  @DisplayName("예약 신청이 거절되면 함께 처리한 예약 만료도 취소된다")
  void rollsBackExpiryWhenReservationIsRejected() {
    // given
    Performance performance = save(now.minusDays(1), now.plusDays(1), 10, 8);
    Reservation overdue = saveOverdue(performance, 1);

    // when
    Throwable thrown =
        catchThrowable(() -> reservationService.reserve(memberId, performance.getId(), 3));

    // then
    assertThat(thrown).satisfies(hasErrorCode(ErrorCode.INSUFFICIENT_STOCK));
    assertThat(((BusinessException) thrown).getProperties()).containsEntry("remainingQuantity", 2);
    assertThat(storedStatusOf(overdue)).isEqualTo(ReservationStatus.PENDING_PAYMENT);
    assertThat(remainingOf(performance)).isEqualTo(1);
  }

  @Test
  @DisplayName("공연 수정이 거절되면 함께 처리한 예약 만료도 취소된다")
  void rollsBackExpiryWhenUpdateIsRejected() {
    // given
    Performance performance = save(now.minusDays(1), now.plusDays(1), 100, 5);
    Reservation overdue = saveOverdue(performance, 3);

    // when
    Throwable thrown =
        catchThrowable(
            () ->
                performanceService.update(
                    performance.getId(),
                    infoOf(performance, "공연", performance.getSalePeriod().saleStartAt()),
                    4,
                    IfMatch.parse(IfMatch.eTagOf(performance.getVersion()))));

    // then
    assertThat(thrown).satisfies(hasErrorCode(ErrorCode.TOTAL_QUANTITY_BELOW_RESERVED));
    assertThat(((BusinessException) thrown).getProperties()).containsEntry("reservedQuantity", 5);
    assertThat(storedStatusOf(overdue)).isEqualTo(ReservationStatus.PENDING_PAYMENT);
    assertThat(stockRepository.findById(performance.getId()).orElseThrow().getReservedQuantity())
        .isEqualTo(8);
  }

  @ParameterizedTest(name = "{0}")
  @DisplayName("만료된 예약은 공연 수정 시 예약 수량에 포함하지 않는다")
  @MethodSource("expiredReservationUpdateCases")
  void ignoresExpiredReservationsOnUpdate(
      String situation, boolean changeSaleStart, int totalQuantity) {
    // given
    Performance performance = save(now.minusDays(1), now.plusDays(1), 100, 0);
    saveOverdue(performance, 3);
    LocalDateTime saleStartAt =
        changeSaleStart ? now.plusHours(1) : performance.getSalePeriod().saleStartAt();

    // when
    performanceService.update(
        performance.getId(),
        infoOf(performance, "공연", saleStartAt),
        totalQuantity,
        IfMatch.parse(IfMatch.eTagOf(performance.getVersion())));

    // then
    Performance saved = performanceRepository.findById(performance.getId()).orElseThrow();
    PerformanceStock stock = stockRepository.findById(performance.getId()).orElseThrow();
    assertThat(saved.getSalePeriod().saleStartAt()).isEqualTo(saleStartAt);
    assertThat(stock.getTotalQuantity()).isEqualTo(totalQuantity);
    assertThat(stock.getReservedQuantity()).isZero();
  }

  static Stream<Arguments> expiredReservationUpdateCases() {
    return Stream.of(arguments("총 판매 수량 축소", false, 2), arguments("판매 시작 일시 변경", true, 100));
  }

  @Test
  @DisplayName("이미 만료된 예약은 스케줄러가 다시 만료하지 않는다")
  void schedulerSkipsAlreadyExpiredReservations() {
    // given
    Performance performance = save(now.minusDays(1), now.plusDays(1), 10, 0);
    Reservation overdue = saveOverdue(performance, 3);
    reservationService.reserve(memberId, performance.getId(), 1);

    // when
    expiryScheduler.expireOverdue();

    // then
    assertThat(storedStatusOf(overdue)).isEqualTo(ReservationStatus.EXPIRED);
    assertThat(remainingOf(performance)).isEqualTo(9);
  }

  @Test
  @DisplayName("스케줄러는 결제 제한 시각이 지난 예약만 만료하고 재고를 복구한다")
  void schedulerExpiresOnlyOverdueReservations() {
    // given
    Performance first = save(now.minusDays(1), now.plusDays(1), 100, 0);
    Performance second = save(now.minusDays(1), now.plusDays(1), 50, 0);
    Reservation reserved = reservationService.reserve(memberId, first.getId(), 1);
    Reservation overdueFirst = saveOverdue(first, 2);
    Reservation overdueSecond = saveOverdue(second, 3);

    // when
    expiryScheduler.expireOverdue();

    // then
    assertThat(storedStatusOf(reserved)).isEqualTo(ReservationStatus.PENDING_PAYMENT);
    assertThat(storedStatusOf(overdueFirst)).isEqualTo(ReservationStatus.EXPIRED);
    assertThat(storedStatusOf(overdueSecond)).isEqualTo(ReservationStatus.EXPIRED);
    assertThat(remainingOf(first)).isEqualTo(99);
    assertThat(remainingOf(second)).isEqualTo(50);
  }

  @Test
  @DisplayName("스케줄러는 다른 트랜잭션이 재고 락을 보유한 공연을 기다리지 않고 건너뛴다")
  void expireOverdueDoesNotWaitForLockedPerformance() throws Exception {
    // given
    Performance lockedPerformance = save(now.minusDays(1), now.plusDays(1), 100, 0);
    Reservation lockedOverdue = saveOverdue(lockedPerformance, 1);
    Duration lockWaitTimeout =
        Duration.ofSeconds(
            Objects.requireNonNull(
                jdbcTemplate.queryForObject("select @@innodb_lock_wait_timeout", Long.class)));

    // when
    runWhileOtherTransactionHoldsLock(
        transactionManager,
        () -> stockRepository.findByIdForUpdate(lockedPerformance.getId()),
        () ->
            assertTimeout(
                lockWaitTimeout.dividedBy(2),
                () -> reservationService.expireOverdue(lockedPerformance.getId())));

    // then
    assertThat(storedStatusOf(lockedOverdue)).isEqualTo(ReservationStatus.PENDING_PAYMENT);
    assertThat(remainingOf(lockedPerformance)).isEqualTo(99);
  }

  @Test
  @DisplayName("스케줄러와 예약 신청이 같은 예약을 동시에 만료해도 재고를 중복으로 복구하지 않는다")
  void releasesExpiredQuantityOnceUnderConcurrency() throws Exception {
    // given
    Performance performance = save(now.minusDays(1), now.plusDays(1), 10, 0);
    saveOverdue(performance, 3);

    // when
    runBlockedByOtherTransactionLock(
        transactionManager,
        jdbcTemplate,
        () -> stockLocker.tryLockAndExpireOverdue(performance.getId()),
        () -> reservationService.reserve(memberId, performance.getId(), 1));

    // then
    assertThat(remainingOf(performance)).isEqualTo(9);
  }

  @Test
  @DisplayName("만료 처리 중인 공연을 수정하면 만료가 반영된 예약 수량을 기준으로 판단한다")
  void updatesAgainstStockReleasedByWaitedExpiry() throws Exception {
    // given
    Performance performance = save(now.minusDays(1), now.plusDays(1), 10, 0);
    Reservation overdue = saveOverdue(performance, 3);

    // when
    runBlockedByOtherTransactionLock(
        transactionManager,
        jdbcTemplate,
        () -> stockLocker.tryLockAndExpireOverdue(performance.getId()),
        () ->
            performanceService.update(
                performance.getId(),
                infoOf(performance, "공연", performance.getSalePeriod().saleStartAt()),
                2,
                IfMatch.parse(IfMatch.eTagOf(performance.getVersion()))));

    // then
    PerformanceStock stock = stockRepository.findById(performance.getId()).orElseThrow();
    assertThat(stock.getTotalQuantity()).isEqualTo(2);
    assertThat(stock.getReservedQuantity()).isZero();
    assertThat(storedStatusOf(overdue)).isEqualTo(ReservationStatus.EXPIRED);
  }

  @ParameterizedTest(name = "제한 시각 {0}ns → {1}")
  @DisplayName("재고 락 획득 시 결제 제한 시각 경계를 기준으로 예약을 만료한다")
  @CsvSource({"-1, PENDING_PAYMENT", "0, EXPIRED"})
  void expiresAtDeadlineBoundaryOnLock(long nanosFromDeadline, ReservationStatus status) {
    // given
    Performance performance = save(now.minusDays(1), now.plusDays(1), 100, 1);
    Reservation reservation =
        reservationRepository.save(Reservation.create(memberId, performance, 1, now));
    LocalDateTime lockedAt = reservation.getPaymentDeadlineAt().plusNanos(nanosFromDeadline);
    Clock fixed = Clock.fixed(lockedAt.atZone(clock.getZone()).toInstant(), clock.getZone());
    PerformanceStockLocker locker =
        new PerformanceStockLocker(stockRepository, overdueReservationExpirer, fixed);

    // when
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(s -> locker.lockAndExpireOverdue(performance.getId()));

    // then
    assertThat(storedStatusOf(reservation)).isEqualTo(status);
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
    Reservation expired = saveOverdue(performance, 1);
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
    saveOverdue(performance, 1);
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
    Reservation expired = saveOverdue(performance, 1);

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

  private Reservation saveOverdue(Performance performance, int quantity) {
    PerformanceStock stock = stockRepository.findById(performance.getId()).orElseThrow();
    stock.reserve(quantity);
    stockRepository.save(stock);
    return reservationRepository.save(
        Reservation.create(memberId, performance, quantity, now.minusMinutes(11)));
  }

  private ReservationStatus storedStatusOf(Reservation reservation) {
    return reservationRepository.findById(reservation.getId()).orElseThrow().getStatus();
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

  private void update(Performance performance) {
    performanceService.update(
        performance.getId(),
        infoOf(performance, "수정된 공연", performance.getSalePeriod().saleStartAt()),
        100,
        IfMatch.parse(IfMatch.eTagOf(performance.getVersion())));
  }

  private void updateSaleStartAt(Performance performance, LocalDateTime saleStartAt) {
    performanceService.update(
        performance.getId(),
        infoOf(performance, "공연", saleStartAt),
        100,
        IfMatch.parse(IfMatch.eTagOf(performance.getVersion())));
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

  private static boolean succeeded(Runnable task) {
    try {
      task.run();
      return true;
    } catch (BusinessException e) {
      return false;
    }
  }
}
