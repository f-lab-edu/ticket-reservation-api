package com.ticketing.reservation.performance.repository;

import com.ticketing.reservation.performance.domain.PerformanceStock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PerformanceStockRepository extends JpaRepository<PerformanceStock, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from PerformanceStock s where s.performanceId = :performanceId")
  Optional<PerformanceStock> findByIdForUpdate(@Param("performanceId") Long performanceId);

  @Query(
      value =
          """
          select * from performance_stock
          where performance_id = :performanceId
          for update skip locked
          """,
      nativeQuery = true)
  Optional<PerformanceStock> findByIdForUpdateSkipLocked(
      @Param("performanceId") Long performanceId);
}
