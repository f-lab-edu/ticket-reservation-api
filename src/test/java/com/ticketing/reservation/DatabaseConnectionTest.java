package com.ticketing.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;

// Docker Compose로 실행한 실제 MySQL에 접속되는지 확인한다.
// MySQL 컨테이너가 실행 중이고 DB_PASSWORD 환경변수가 설정되어 있어야 한다.
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DatabaseConnectionTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void selectOne() {
		Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);

		assertThat(result).isEqualTo(1);
	}

	@Test
	void connectsToTicketingDatabase() {
		String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);

		assertThat(database).isEqualTo("ticketing");
	}
}
