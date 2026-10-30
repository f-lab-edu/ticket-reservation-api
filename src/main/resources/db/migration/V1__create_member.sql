CREATE TABLE member (
    id       BIGINT       NOT NULL AUTO_INCREMENT,
    email    VARCHAR(254) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role     VARCHAR(20)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_member_email UNIQUE (email)
);
