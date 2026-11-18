package com.ticketing.reservation.performance.repository;

import com.ticketing.reservation.performance.domain.Performance;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {

  List<Performance> findAllByOrderByPerformanceAtAscIdAsc();

  @Query(
      """
      select p from Performance p
      join PerformanceStock s on s.performanceId = p.id
      where p.salePeriod.saleStartAt <= :now
        and p.salePeriod.saleEndAt > :now
        and s.reservedQuantity < s.totalQuantity
      order by p.performanceAt asc, p.id asc
      """)
  List<Performance> findAvailable(@Param("now") LocalDateTime now);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Performance p where p.id = :performanceId")
  Optional<Performance> findByIdForUpdate(@Param("performanceId") Long performanceId);
}
