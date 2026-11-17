package com.ticketing.reservation.performance.repository;

import com.ticketing.reservation.performance.domain.PerformanceStock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerformanceStockRepository extends JpaRepository<PerformanceStock, Long> {}
