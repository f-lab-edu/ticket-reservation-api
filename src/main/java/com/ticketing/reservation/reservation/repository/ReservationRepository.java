package com.ticketing.reservation.reservation.repository;

import com.ticketing.reservation.reservation.domain.Reservation;
import com.ticketing.reservation.reservation.domain.ReservationStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

  List<Reservation> findAllByMemberIdOrderByIdDesc(Long memberId);

  @Query(
      """
      select r from Reservation r
      where r.memberId = :memberId
        and (case when r.status = PENDING_PAYMENT and r.paymentDeadlineAt <= :now
                  then com.ticketing.reservation.reservation.domain.ReservationStatus.EXPIRED
                  else r.status end) in :statuses
      order by r.id desc
      """)
  List<Reservation> findAllByMemberIdAndStatusAt(
      @Param("memberId") Long memberId,
      @Param("statuses") Collection<ReservationStatus> statuses,
      @Param("now") LocalDateTime now);

  Optional<Reservation> findByIdAndMemberId(Long id, Long memberId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select r from Reservation r
      where r.performanceId = :performanceId
        and r.status = PENDING_PAYMENT
        and r.paymentDeadlineAt <= :now
      """)
  List<Reservation> findOverdueForUpdate(
      @Param("performanceId") Long performanceId, @Param("now") LocalDateTime now);

  @Query(
      """
      select distinct r.performanceId from Reservation r
      where r.status = PENDING_PAYMENT
        and r.paymentDeadlineAt <= :now
      """)
  List<Long> findPerformanceIdsWithOverdue(@Param("now") LocalDateTime now);
}
