package com.ticketing.reservation.integration.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.ticketing.reservation.integration.TestcontainersConfiguration;
import com.ticketing.reservation.performance.domain.Performance;
import com.ticketing.reservation.performance.domain.PerformanceStock;
import com.ticketing.reservation.performance.domain.SalePeriod;
import com.ticketing.reservation.performance.repository.PerformanceRepository;
import com.ticketing.reservation.performance.repository.PerformanceStockRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class PerformanceRepositoryTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 1, 12, 0);
  private static final LocalDateTime PERFORMANCE_AT = NOW.plusDays(30);

  @Autowired private PerformanceRepository performanceRepository;
  @Autowired private PerformanceStockRepository stockRepository;

  @Test
  @DisplayName("예약 가능한 공연은 판매 기간 안이고 잔여 수량이 있는 공연만 조회한다")
  void findsOnlyAvailablePerformances() {
    // given
    save("판매 중", NOW.minusDays(1), NOW.plusDays(1), 99);
    save("판매 예정", NOW.plusDays(1), NOW.plusDays(2), 0);
    save("판매 종료", NOW.minusDays(2), NOW.minusDays(1), 0);
    save("매진", NOW.minusDays(1), NOW.plusDays(1), 100);

    // when
    List<Performance> performances = performanceRepository.findAvailable(NOW);

    // then
    assertThat(performances).extracting(Performance::getTitle).containsExactly("판매 중");
  }

  @Test
  @DisplayName("판매 시작 일시와 현재 시각이 같으면 포함하고 판매 종료 일시와 같으면 제외한다")
  void includesSaleStartAndExcludesSaleEndAtNow() {
    // given
    save("지금 판매 시작", NOW, NOW.plusDays(1), 0);
    save("지금 판매 종료", NOW.minusDays(1), NOW, 0);

    // when
    List<Performance> performances = performanceRepository.findAvailable(NOW);

    // then
    assertThat(performances).extracting(Performance::getTitle).containsExactly("지금 판매 시작");
  }

  @ParameterizedTest(name = "{0}")
  @DisplayName("공연 목록은 공연 일시 순으로, 같으면 등록 순으로 조회한다")
  @MethodSource("listQueries")
  void findsOrderedByPerformanceAtAndId(Function<PerformanceRepository, List<Performance>> query) {
    // given
    save("다음 날 공연", PERFORMANCE_AT.plusDays(1));
    save("첫날 공연 1", PERFORMANCE_AT);
    save("첫날 공연 2", PERFORMANCE_AT);

    // when
    List<Performance> performances = query.apply(performanceRepository);

    // then
    assertThat(performances)
        .extracting(Performance::getTitle)
        .containsExactly("첫날 공연 1", "첫날 공연 2", "다음 날 공연");
  }

  static Stream<Arguments> listQueries() {
    Function<PerformanceRepository, List<Performance>> available =
        repository -> repository.findAvailable(NOW);
    Function<PerformanceRepository, List<Performance>> all =
        PerformanceRepository::findAllByOrderByPerformanceAtAscIdAsc;
    return Stream.of(arguments(named("예약 가능한 공연", available)), arguments(named("전체 공연", all)));
  }

  private void save(String title, LocalDateTime performanceAt) {
    save(title, performanceAt, NOW.minusDays(1), NOW.plusDays(1), 0);
  }

  private void save(
      String title, LocalDateTime saleStartAt, LocalDateTime saleEndAt, int reservedQuantity) {
    save(title, PERFORMANCE_AT, saleStartAt, saleEndAt, reservedQuantity);
  }

  private void save(
      String title,
      LocalDateTime performanceAt,
      LocalDateTime saleStartAt,
      LocalDateTime saleEndAt,
      int reservedQuantity) {
    Performance performance =
        performanceRepository.save(
            Performance.create(
                title,
                "공연장",
                performanceAt,
                50_000,
                new SalePeriod(saleStartAt, saleEndAt),
                saleStartAt.minusDays(1)));
    PerformanceStock stock = PerformanceStock.create(performance.getId(), 100);
    ReflectionTestUtils.setField(stock, "reservedQuantity", reservedQuantity);
    stockRepository.save(stock);
  }
}
