package com.ticketing.reservation.integration.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketing.reservation.integration.TestcontainersConfiguration;
import com.ticketing.reservation.performance.domain.Performance;
import com.ticketing.reservation.performance.dto.PerformanceRequest;
import com.ticketing.reservation.performance.repository.PerformanceRepository;
import com.ticketing.reservation.performance.service.PerformanceService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PerformanceServiceTest {

  @Autowired private PerformanceService performanceService;
  @Autowired private PerformanceRepository performanceRepository;
  @Autowired private Clock clock;

  @AfterEach
  void tearDown() {
    performanceRepository.deleteAllInBatch();
  }

  @Test
  @DisplayName("공연을 등록하면 요청한 정보를 저장한다")
  void savesPerformanceWithRequestValues() {
    // given
    LocalDateTime performanceAt =
        LocalDateTime.now(clock).plusDays(30).truncatedTo(ChronoUnit.SECONDS);
    LocalDateTime saleStartAt = performanceAt.minusDays(29);
    LocalDateTime saleEndAt = performanceAt.minusDays(1);
    PerformanceRequest request =
        new PerformanceRequest("공연", "공연장", performanceAt, 50_000, 100, saleStartAt, saleEndAt);

    // when
    Performance performance = performanceService.register(request);

    // then
    Performance saved = performanceRepository.findById(performance.getId()).orElseThrow();
    assertThat(saved.getTitle()).isEqualTo("공연");
    assertThat(saved.getVenue()).isEqualTo("공연장");
    assertThat(saved.getPerformanceAt()).isEqualTo(performanceAt);
    assertThat(saved.getPrice()).isEqualTo(50_000);
    assertThat(saved.getTotalQuantity()).isEqualTo(100);
    assertThat(saved.getSaleStartAt()).isEqualTo(saleStartAt);
    assertThat(saved.getSaleEndAt()).isEqualTo(saleEndAt);
  }
}
