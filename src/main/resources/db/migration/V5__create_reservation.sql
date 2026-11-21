CREATE TABLE reservation (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    member_id           BIGINT      NOT NULL,
    performance_id      BIGINT      NOT NULL,
    quantity            INT         NOT NULL,
    unit_price          INT         NOT NULL,
    status              VARCHAR(20) NOT NULL,
    reserved_at         DATETIME(6) NOT NULL,
    payment_deadline_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_reservation_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_reservation_performance FOREIGN KEY (performance_id) REFERENCES performance (id)
);
