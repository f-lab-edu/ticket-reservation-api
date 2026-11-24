package com.ticketing.reservation.reservation.service;

import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import com.ticketing.reservation.performance.domain.Performance;
import com.ticketing.reservation.performance.domain.PerformanceStock;
import com.ticketing.reservation.performance.repository.PerformanceRepository;
import com.ticketing.reservation.performance.service.PerformanceStockLocker;
import com.ticketing.reservation.reservation.domain.Reservation;
import com.ticketing.reservation.reservation.domain.ReservationDetail;
import com.ticketing.reservation.reservation.domain.ReservationStatus;
import com.ticketing.reservation.reservation.repository.ReservationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {

  private final ReservationRepository reservationRepository;
  private final PerformanceRepository performanceRepository;
  private final PerformanceStockLocker stockLocker;
  private final Clock clock;

  public ReservationService(
      ReservationRepository reservationRepository,
      PerformanceRepository performanceRepository,
      PerformanceStockLocker stockLocker,
      Clock clock) {
    this.reservationRepository = reservationRepository;
    this.performanceRepository = performanceRepository;
    this.stockLocker = stockLocker;
    this.clock = clock;
  }

  @Transactional
  public Reservation reserve(Long memberId, Long performanceId, int quantity) {
    PerformanceStock stock = stockLocker.lock(performanceId);
    Performance performance =
        performanceRepository
            .findById(performanceId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PERFORMANCE_NOT_FOUND));
    Reservation reservation =
        Reservation.create(memberId, performance, quantity, LocalDateTime.now(clock));
    stock.reserve(quantity);
    return reservationRepository.save(reservation);
  }

  @Transactional(readOnly = true)
  public List<ReservationDetail> getMyReservations(Long memberId, Set<ReservationStatus> statuses) {
    LocalDateTime now = LocalDateTime.now(clock);
    List<Reservation> reservations =
        statuses.isEmpty()
            ? reservationRepository.findAllByMemberIdOrderByIdDesc(memberId)
            : reservationRepository.findAllByMemberIdAndStatusAt(memberId, statuses, now);
    Set<Long> performanceIds =
        reservations.stream().map(Reservation::getPerformanceId).collect(Collectors.toSet());
    Map<Long, Performance> performances =
        performanceRepository.findAllById(performanceIds).stream()
            .collect(Collectors.toMap(Performance::getId, Function.identity()));
    return reservations.stream()
        .map(r -> toDetail(r, performances.get(r.getPerformanceId()), now))
        .toList();
  }

  @Transactional(readOnly = true)
  public ReservationDetail getMyReservation(Long memberId, Long reservationId) {
    Reservation reservation =
        reservationRepository
            .findByIdAndMemberId(reservationId, memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
    Performance performance =
        performanceRepository.findById(reservation.getPerformanceId()).orElseThrow();
    return toDetail(reservation, performance, LocalDateTime.now(clock));
  }

  private static ReservationDetail toDetail(
      Reservation reservation, Performance performance, LocalDateTime now) {
    return new ReservationDetail(reservation, performance, reservation.statusAt(now));
  }
}
