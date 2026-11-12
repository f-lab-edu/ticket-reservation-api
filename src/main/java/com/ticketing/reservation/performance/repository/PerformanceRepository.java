package com.ticketing.reservation.performance.repository;

import com.ticketing.reservation.performance.domain.Performance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {}
