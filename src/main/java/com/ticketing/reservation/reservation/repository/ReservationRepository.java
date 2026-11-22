package com.ticketing.reservation.reservation.repository;

import com.ticketing.reservation.reservation.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {}
