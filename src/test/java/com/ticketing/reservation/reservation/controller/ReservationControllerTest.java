package com.ticketing.reservation.reservation.controller;

import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import com.ticketing.reservation.member.domain.Member;
import com.ticketing.reservation.performance.domain.Performance;
import com.ticketing.reservation.performance.domain.PerformanceInfo;
import com.ticketing.reservation.reservation.domain.Reservation;
import com.ticketing.reservation.reservation.service.ReservationService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

@WebMvcTest(ReservationController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class ReservationControllerTest {

  private static final long MEMBER_ID = 7L;
  private static final Map<String, Object> VALID_FIELDS = Map.of("performanceId", 1, "quantity", 2);

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtEncoder jwtEncoder;
  @Autowired private AuthProperties properties;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private ReservationService reservationService;
  @MockitoBean private RefreshTokenRepository refreshTokenRepository;

  private String memberToken;

  @BeforeEach
  void setUp() {
    Member member = Member.create("member@example.com", "{bcrypt}hash");
    ReflectionTestUtils.setField(member, "id", MEMBER_ID);
    memberToken =
        new AccessTokenProvider(jwtEncoder, properties, Clock.systemUTC()).issue(member, "family");
  }

  @Test
  @DisplayName("예약에 성공하면 201과 예약 정보를 반환한다")
  void returnsCreatedWithReservationWhenSucceeds() throws Exception {
    // given
    given(reservationService.reserve(MEMBER_ID, 1L, 2)).willReturn(reservation());

    // when
    ResultActions result = reserve(memberToken, VALID_FIELDS);

    // then
    result
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(10))
        .andExpect(jsonPath("$.performanceId").value(1))
        .andExpect(jsonPath("$.quantity").value(2))
        .andExpect(jsonPath("$.unitPrice").value(50000))
        .andExpect(jsonPath("$.totalPrice").value(100000))
        .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
        .andExpect(jsonPath("$.reservedAt").value("2026-12-05T12:00:00.123456"))
        .andExpect(jsonPath("$.paymentDeadlineAt").value("2026-12-05T12:10:00.123456"));
  }

  @ParameterizedTest(name = "{0}: {1}")
  @DisplayName("요청값이 유효하지 않으면 400을 반환한다")
  @MethodSource("invalidFields")
  void returnsBadRequestWhenRequestIsInvalid(String field, Object value) throws Exception {
    // given
    Map<String, Object> body = new HashMap<>(VALID_FIELDS);
    body.put(field, value);

    // when
    ResultActions result = reserve(memberToken, body);

    // then
    result.andExpect(status().isBadRequest());
    then(reservationService).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("잔여 판매 수량이 부족하면 409와 오류 코드, 현재 잔여 수량을 반환한다")
  void returnsConflictWhenStockIsInsufficient() throws Exception {
    // given
    given(reservationService.reserve(anyLong(), anyLong(), anyInt()))
        .willThrow(
            new BusinessException(ErrorCode.INSUFFICIENT_STOCK, Map.of("remainingQuantity", 2)));

    // when
    ResultActions result = reserve(memberToken, VALID_FIELDS);

    // then
    result
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
        .andExpect(jsonPath("$.remainingQuantity").value(2));
  }

  static Stream<Arguments> invalidFields() {
    return Stream.of(
        arguments("performanceId", null),
        arguments("performanceId", "abc"),
        arguments("quantity", null),
        arguments("quantity", 0),
        arguments("quantity", -1),
        arguments("quantity", 1.5));
  }

  private ResultActions reserve(String token, Map<String, Object> body) throws Exception {
    var request =
        post("/api/v1/reservations")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body));
    if (token != null) {
      request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
    return mockMvc.perform(request);
  }

  private static Reservation reservation() {
    LocalDateTime now = LocalDateTime.parse("2026-12-05T12:00:00.123456");
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
    ReflectionTestUtils.setField(performance, "id", 1L);
    Reservation reservation = Reservation.create(MEMBER_ID, performance, 2, now);
    ReflectionTestUtils.setField(reservation, "id", 10L);
    return reservation;
  }
}
