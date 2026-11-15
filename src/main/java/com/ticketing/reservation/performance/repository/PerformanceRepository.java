package com.ticketing.reservation.performance.repository;

import com.ticketing.reservation.performance.domain.Performance;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {

  List<Performance> findAllByOrderByPerformanceAtAscIdAsc();

  @Query(
      """
      select p from Performance p
      where p.salePeriod.saleStartAt <= :now
        and p.salePeriod.saleEndAt > :now
        and p.remainingQuantity > 0
      order by p.performanceAt asc, p.id asc
      """)
  List<Performance> findAvailable(@Param("now") LocalDateTime now);
}
