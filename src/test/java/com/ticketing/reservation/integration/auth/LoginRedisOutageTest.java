package com.ticketing.reservation.integration.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketing.reservation.member.repository.MemberRepository;
import com.ticketing.reservation.member.service.MemberService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = "spring.data.redis.port=1")
@AutoConfigureMockMvc
@Import(LoginRedisOutageTest.MySqlOnlyConfiguration.class)
class LoginRedisOutageTest {

  private static final String EMAIL = "outage@example.com";
  private static final String PASSWORD = "password";

  @Autowired private MockMvc mockMvc;
  @Autowired private MemberService memberService;
  @Autowired private MemberRepository memberRepository;

  @AfterEach
  void tearDown() {
    memberRepository.deleteAllInBatch();
  }

  @Test
  @DisplayName("Redis 장애 중 로그인하면 503을 반환한다")
  void returnsServiceUnavailableOnLogin() throws Exception {
    // given
    memberService.signUp(EMAIL, PASSWORD);

    // when
    ResultActions result =
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "%s", "password": "%s"}
                    """
                        .formatted(EMAIL, PASSWORD)));

    // then
    result
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("AUTH_STORE_UNAVAILABLE"));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class MySqlOnlyConfiguration {

    @Bean
    @ServiceConnection
    MySQLContainer<?> mysqlContainer() {
      return new MySQLContainer<>(DockerImageName.parse("mysql:8.4.6"))
          .withDatabaseName("ticketing");
    }
  }
}
