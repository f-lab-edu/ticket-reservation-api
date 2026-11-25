CREATE INDEX idx_reservation_status_deadline
    ON reservation (status, payment_deadline_at);

CREATE INDEX idx_reservation_performance_status_deadline
    ON reservation (performance_id, status, payment_deadline_at);
