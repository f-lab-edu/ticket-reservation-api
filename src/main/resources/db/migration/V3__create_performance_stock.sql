CREATE TABLE performance_stock (
    performance_id    BIGINT NOT NULL,
    total_quantity    INT    NOT NULL,
    reserved_quantity INT    NOT NULL,
    PRIMARY KEY (performance_id),
    CONSTRAINT fk_performance_stock_performance FOREIGN KEY (performance_id) REFERENCES performance (id)
);

INSERT INTO performance_stock (performance_id, total_quantity, reserved_quantity)
SELECT id, total_quantity, total_quantity - remaining_quantity
FROM performance;

ALTER TABLE performance
    DROP COLUMN total_quantity,
    DROP COLUMN remaining_quantity;
