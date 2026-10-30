package com.ticketing.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

// Testcontainers로 띄운 실제 MySQL에 접속되는지 확인한다.
// Docker가 실행 중이어야 한다.
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class DatabaseConnectionTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void selectOne() {
    Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);

    assertThat(result).isEqualTo(2);
  }

  @Test
  void connectsToTicketingDatabase() {
    String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);

    assertThat(database).isEqualTo("ticketing");
  }
}
