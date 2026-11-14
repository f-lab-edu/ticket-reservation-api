package com.ticketing.reservation.integration.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketing.reservation.integration.TestcontainersConfiguration;
import com.ticketing.reservation.performance.domain.Performance;
import com.ticketing.reservation.performance.domain.SalePeriod;
import com.ticketing.reservation.performance.repository.PerformanceRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PerformanceRepositoryTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 1, 12, 0);
  private static final LocalDateTime PERFORMANCE_AT = NOW.plusDays(30);

  @Autowired private PerformanceRepository performanceRepository;

  @AfterEach
  void tearDown() {
    performanceRepository.deleteAllInBatch();
  }

  @Test
  @DisplayName("예약 가능한 공연은 판매 기간 안이고 잔여 수량이 있는 공연만 조회한다")
  void findsOnlyAvailablePerformances() {
    // given
    save("판매 중", NOW.minusDays(1), NOW.plusDays(1), 100);
    save("판매 예정", NOW.plusDays(1), NOW.plusDays(2), 100);
    save("판매 종료", NOW.minusDays(2), NOW.minusDays(1), 100);
    save("매진", NOW.minusDays(1), NOW.plusDays(1), 0);

    // when
    List<Performance> performances = performanceRepository.findAvailable(NOW);

    // then
    assertThat(performances).extracting(Performance::getTitle).containsExactly("판매 중");
  }

  @Test
  @DisplayName("판매 시작 일시와 현재 시각이 같으면 포함하고 판매 종료 일시와 같으면 제외한다")
  void includesSaleStartAndExcludesSaleEndAtNow() {
    // given
    save("지금 판매 시작", NOW, NOW.plusDays(1), 100);
    save("지금 판매 종료", NOW.minusDays(1), NOW, 100);

    // when
    List<Performance> performances = performanceRepository.findAvailable(NOW);

    // then
    assertThat(performances).extracting(Performance::getTitle).containsExactly("지금 판매 시작");
  }

  @Test
  @DisplayName("전체 공연은 공연 일시 순으로, 같으면 등록 순으로 조회한다")
  void findsAllOrderedByPerformanceAtAndId() {
    // given
    save("다음 날 공연", PERFORMANCE_AT.plusDays(1));
    save("첫날 공연 1", PERFORMANCE_AT);
    save("첫날 공연 2", PERFORMANCE_AT);

    // when
    List<Performance> performances = performanceRepository.findAllByOrderByPerformanceAtAscIdAsc();

    // then
    assertThat(performances)
        .extracting(Performance::getTitle)
        .containsExactly("첫날 공연 1", "첫날 공연 2", "다음 날 공연");
  }

  private void save(String title, LocalDateTime performanceAt) {
    save(title, performanceAt, NOW.minusDays(1), NOW.plusDays(1), 100);
  }

  private void save(
      String title, LocalDateTime saleStartAt, LocalDateTime saleEndAt, int remainingQuantity) {
    save(title, PERFORMANCE_AT, saleStartAt, saleEndAt, remainingQuantity);
  }

  private void save(
      String title,
      LocalDateTime performanceAt,
      LocalDateTime saleStartAt,
      LocalDateTime saleEndAt,
      int remainingQuantity) {
    Performance performance =
        Performance.create(
            title,
            "공연장",
            performanceAt,
            50_000,
            100,
            new SalePeriod(saleStartAt, saleEndAt),
            saleStartAt.minusDays(1));
    ReflectionTestUtils.setField(performance, "remainingQuantity", remainingQuantity);
    performanceRepository.save(performance);
  }
}
