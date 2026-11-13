CREATE TABLE performance (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    title              VARCHAR(100) NOT NULL,
    venue              VARCHAR(100) NOT NULL,
    performance_at     DATETIME     NOT NULL,
    price              INT          NOT NULL,
    total_quantity     INT          NOT NULL,
    remaining_quantity INT          NOT NULL,
    sale_start_at      DATETIME     NOT NULL,
    sale_end_at        DATETIME     NOT NULL,
    PRIMARY KEY (id)
);
