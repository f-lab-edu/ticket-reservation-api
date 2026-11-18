package com.ticketing.reservation.performance.controller;

import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.reservation.auth.config.AuthProperties;
import com.ticketing.reservation.auth.config.JwtConfig;
import com.ticketing.reservation.auth.repository.RefreshTokenRepository;
import com.ticketing.reservation.auth.token.AccessTokenProvider;
import com.ticketing.reservation.global.config.SecurityConfig;
import com.ticketing.reservation.global.error.BusinessException;
import com.ticketing.reservation.global.error.ErrorCode;
import com.ticketing.reservation.global.web.IfMatch;
import com.ticketing.reservation.member.domain.Member;
import com.ticketing.reservation.member.domain.MemberRole;
import com.ticketing.reservation.performance.domain.Performance;
import com.ticketing.reservation.performance.domain.PerformanceDetail;
import com.ticketing.reservation.performance.domain.PerformanceInfo;
import com.ticketing.reservation.performance.domain.PerformanceStock;
import com.ticketing.reservation.performance.service.PerformanceService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(PerformanceController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class PerformanceControllerTest {

  private static final Map<String, Object> VALID_FIELDS =
      Map.of(
          "title", "공연",
          "venue", "공연장",
          "performanceAt", "2026-12-20T19:00:00",
          "price", 50000,
          "totalQuantity", 100,
          "saleStartAt", "2026-12-01T10:00:00",
          "saleEndAt", "2026-12-19T18:00:00");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtEncoder jwtEncoder;
  @Autowired private AuthProperties properties;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private PerformanceService performanceService;
  @MockitoBean private RefreshTokenRepository refreshTokenRepository;

  private String adminToken;

  @BeforeEach
  void setUp() {
    Member admin = Member.create("admin@example.com", "{bcrypt}hash");
    ReflectionTestUtils.setField(admin, "role", MemberRole.ADMIN);
    ReflectionTestUtils.setField(admin, "id", 1L);
    adminToken =
        new AccessTokenProvider(jwtEncoder, properties, Clock.systemUTC()).issue(admin, "family");
  }

  @Nested
  @DisplayName("공연 등록")
  class Register {

    @Test
    @DisplayName("등록에 성공하면 201과 공연 정보를 반환한다")
    void returnsCreatedWithPerformanceWhenSucceeds() throws Exception {
      // given
      given(performanceService.register(any(PerformanceInfo.class), anyInt()))
          .willReturn(detail(1L));

      // when
      ResultActions result = register(VALID_FIELDS);

      // then
      result
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.id").value(1))
          .andExpect(jsonPath("$.title").value("공연"))
          .andExpect(jsonPath("$.venue").value("공연장"))
          .andExpect(jsonPath("$.performanceAt").value("2026-12-20T19:00:00"))
          .andExpect(jsonPath("$.price").value(50000))
          .andExpect(jsonPath("$.totalQuantity").value(100))
          .andExpect(jsonPath("$.remainingQuantity").value(70))
          .andExpect(jsonPath("$.saleStartAt").value("2026-12-01T10:00:00"))
          .andExpect(jsonPath("$.saleEndAt").value("2026-12-19T18:00:00"))
          .andExpect(header().string(HttpHeaders.ETAG, strongETag(0)));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @DisplayName("요청값이 유효하지 않으면 400을 반환한다")
    @MethodSource("invalidFields")
    void returnsBadRequestWhenRequestIsInvalid(String field, Object value) throws Exception {
      // given
      Map<String, Object> body = new HashMap<>(VALID_FIELDS);
      body.put(field, value);

      // when
      ResultActions result = register(body);

      // then
      result.andExpect(status().isBadRequest());
      then(performanceService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("일시 규칙을 위반하면 400과 오류 코드를 반환한다")
    void returnsBadRequestWhenScheduleIsInvalid() throws Exception {
      // given
      given(performanceService.register(any(PerformanceInfo.class), anyInt()))
          .willThrow(new BusinessException(ErrorCode.SALE_END_AFTER_PERFORMANCE));

      // when
      ResultActions result = register(VALID_FIELDS);

      // then
      result
          .andExpect(status().isBadRequest())
          .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.code").value("SALE_END_AFTER_PERFORMANCE"));
    }

    static Stream<Arguments> invalidFields() {
      return Stream.of(
          arguments("title", " "),
          arguments("title", "가".repeat(Performance.MAX_TITLE_LENGTH + 1)),
          arguments("venue", " "),
          arguments("venue", "가".repeat(Performance.MAX_VENUE_LENGTH + 1)),
          arguments("performanceAt", null),
          arguments("performanceAt", "2026-12-20T19:00:00.5"),
          arguments("price", null),
          arguments("price", -1),
          arguments("totalQuantity", null),
          arguments("totalQuantity", 0),
          arguments("saleStartAt", null),
          arguments("saleStartAt", "2026-12-01T10:00:00.5"),
          arguments("saleEndAt", null),
          arguments("saleEndAt", "2026-12-19T18:00:00.5"));
    }

    private ResultActions register(Map<String, Object> body) throws Exception {
      return mockMvc.perform(
          post("/api/v1/performances")
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(body)));
    }
  }

  @Nested
  @DisplayName("공연 상세 조회")
  class GetPerformance {

    @Test
    @DisplayName("공연이 있으면 200과 공연 정보를 반환한다")
    void returnsPerformanceWhenExists() throws Exception {
      // given
      given(performanceService.getPerformance(1L)).willReturn(detail(1L));

      // when
      ResultActions result = mockMvc.perform(get("/api/v1/performances/1"));

      // then
      result
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(1))
          .andExpect(header().string(HttpHeaders.ETAG, strongETag(0)));
    }

    @Test
    @DisplayName("공연이 없으면 404와 오류 코드를 반환한다")
    void returnsNotFoundWhenPerformanceDoesNotExist() throws Exception {
      // given
      given(performanceService.getPerformance(1L))
          .willThrow(new BusinessException(ErrorCode.PERFORMANCE_NOT_FOUND));

      // when
      ResultActions result = mockMvc.perform(get("/api/v1/performances/1"));

      // then
      result
          .andExpect(status().isNotFound())
          .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.code").value("PERFORMANCE_NOT_FOUND"));
    }
  }

  @Nested
  @DisplayName("공연 정보 수정")
  class Update {

    private static final String IF_MATCH = "\"3\"";

    @Test
    @DisplayName("수정에 성공하면 200과 공연 정보를 반환하고 ETag는 보내지 않는다")
    void returnsOkWithPerformanceWhenSucceeds() throws Exception {
      // given
      PerformanceDetail updated = detail(1L);
      ReflectionTestUtils.setField(updated.performance(), "version", 4L);
      given(
              performanceService.update(
                  eq(1L),
                  any(PerformanceInfo.class),
                  anyInt(),
                  argThat(ifMatch -> ifMatch.matches(3L))))
          .willReturn(updated);

      // when
      ResultActions result = update(VALID_FIELDS);

      // then
      result
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(1))
          .andExpect(header().doesNotExist(HttpHeaders.ETAG));
    }

    @Test
    @DisplayName("요청값이 유효하지 않으면 400을 반환한다")
    void returnsBadRequestWhenRequestIsInvalid() throws Exception {
      // given
      Map<String, Object> body = new HashMap<>(VALID_FIELDS);
      body.put("totalQuantity", 0);

      // when
      ResultActions result = update(body);

      // then
      result.andExpect(status().isBadRequest());
      then(performanceService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("If-Match 헤더가 없으면 428과 오류 코드를 반환한다")
    void returnsPreconditionRequiredWithoutIfMatch() throws Exception {
      // when
      ResultActions result =
          mockMvc.perform(
              put("/api/v1/performances/1")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(VALID_FIELDS)));

      // then
      result
          .andExpect(status().isPreconditionRequired())
          .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.code").value("IF_MATCH_REQUIRED"));
      then(performanceService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("다른 수정이 먼저 반영됐으면 412와 오류 코드를 반환한다")
    void returnsPreconditionFailedWhenPerformanceWasModified() throws Exception {
      // given
      given(
              performanceService.update(
                  eq(1L), any(PerformanceInfo.class), anyInt(), any(IfMatch.class)))
          .willThrow(new BusinessException(ErrorCode.PERFORMANCE_MODIFIED));

      // when
      ResultActions result = update(VALID_FIELDS);

      // then
      result
          .andExpect(status().isPreconditionFailed())
          .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.code").value("PERFORMANCE_MODIFIED"));
    }

    private ResultActions update(Map<String, Object> body) throws Exception {
      return mockMvc.perform(
          put("/api/v1/performances/1")
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
              .header(HttpHeaders.IF_MATCH, IF_MATCH)
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(body)));
    }
  }

  @Nested
  @DisplayName("공연 목록 조회")
  class GetPerformances {

    @Test
    @DisplayName("조건 없이 조회하면 전체 공연 목록을 반환한다")
    void returnsAllPerformancesByDefault() throws Exception {
      // given
      given(performanceService.getPerformances(false)).willReturn(list(1L, 2L));

      // when
      ResultActions result = mockMvc.perform(get("/api/v1/performances"));

      // then
      result
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.performances.length()").value(2))
          .andExpect(jsonPath("$.performances[0].id").value(1))
          .andExpect(jsonPath("$.performances[0].title").value("공연"))
          .andExpect(jsonPath("$.performances[0].venue").value("공연장"))
          .andExpect(jsonPath("$.performances[0].performanceAt").value("2026-12-20T19:00:00"))
          .andExpect(jsonPath("$.performances[0].price").value(50000))
          .andExpect(jsonPath("$.performances[0].remainingQuantity").value(70));
    }

    @Test
    @DisplayName("available이 true이면 예약 가능한 공연 목록을 조회한다")
    void requestsAvailablePerformancesWhenAvailableIsTrue() throws Exception {
      // given
      given(performanceService.getPerformances(true)).willReturn(list(1L));

      // when
      ResultActions result =
          mockMvc.perform(get("/api/v1/performances").param("available", "true"));

      // then
      result.andExpect(status().isOk()).andExpect(jsonPath("$.performances.length()").value(1));
    }
  }

  private static String strongETag(long version) {
    return "\"" + version + "\"";
  }

  private static PerformanceDetail detail(Long id) {
    PerformanceStock stock = PerformanceStock.create(id, 100);
    ReflectionTestUtils.setField(stock, "reservedQuantity", 30);
    return new PerformanceDetail(performance(id), stock);
  }

  private static List<PerformanceDetail> list(Long... ids) {
    return Stream.of(ids).map(PerformanceControllerTest::detail).toList();
  }

  private static Performance performance(Long id) {
    Performance performance =
        Performance.create(
            new PerformanceInfo(
                "공연",
                "공연장",
                LocalDateTime.parse("2026-12-20T19:00:00"),
                50_000,
                LocalDateTime.parse("2026-12-01T10:00:00"),
                LocalDateTime.parse("2026-12-19T18:00:00")),
            LocalDateTime.parse("2026-11-12T12:00:00"));
    ReflectionTestUtils.setField(performance, "id", id);
    return performance;
  }
}
