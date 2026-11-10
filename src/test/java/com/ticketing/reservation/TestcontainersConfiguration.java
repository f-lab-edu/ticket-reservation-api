package com.ticketing.reservation;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

// 테스트용 MySQL·Redis 컨테이너를 띄우고, @ServiceConnection으로 접속 정보를 자동 구성한다.
// 이미지 버전은 로컬 개발 환경(compose.yaml)과 맞춘다.
// TODO: 테스트가 늘어나 느려지면 컨테이너를 static 필드로 두어 모든 테스트 컨텍스트가 하나를 공유하도록 변경한다.
//  현재는 Spring 테스트 컨텍스트마다 컨테이너가 따로 뜬다.
//  공유 시 테스트 간 데이터가 남으므로 @Transactional 롤백 또는 테스트별 데이터 정리를 함께 적용한다.
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  public static final int REDIS_PORT = 6379;

  public static GenericContainer<?> newRedisContainer() {
    return new GenericContainer<>(DockerImageName.parse("redis:8.2.1"))
        .withExposedPorts(REDIS_PORT)
        .withCommand(
            "redis-server",
            "--appendonly",
            "yes",
            "--appendfsync",
            "everysec",
            "--maxmemory-policy",
            "noeviction");
  }

  @Bean
  @ServiceConnection
  public MySQLContainer<?> mysqlContainer() {
    return new MySQLContainer<>(DockerImageName.parse("mysql:8.4.6")).withDatabaseName("ticketing");
  }

  @Bean
  @ServiceConnection(name = "redis")
  public GenericContainer<?> redisContainer() {
    return newRedisContainer();
  }
}
