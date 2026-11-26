package com.ticketing.reservation.reservation.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
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
import com.ticketing.reservation.reservation.domain.ReservationDetail;
import com.ticketing.reservation.reservation.domain.ReservationStatus;
import com.ticketing.reservation.reservation.service.ReservationService;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

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

  @Test
  @DisplayName("내 예약 목록을 조회하면 200과 예약 목록을 반환한다")
  void returnsMyReservations() throws Exception {
    // given
    given(reservationService.getMyReservations(MEMBER_ID, Set.of()))
        .willReturn(List.of(detail(ReservationStatus.PENDING_PAYMENT)));

    // when
    ResultActions result = get("/api/v1/reservations");

    // then
    result
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reservations.length()").value(1))
        .andExpect(jsonPath("$.reservations[0].id").value(10))
        .andExpect(jsonPath("$.reservations[0].performance.id").value(1))
        .andExpect(jsonPath("$.reservations[0].performance.title").value("공연"))
        .andExpect(jsonPath("$.reservations[0].performance.venue").doesNotExist())
        .andExpect(
            jsonPath("$.reservations[0].performance.performanceAt").value("2026-12-20T19:00:00"))
        .andExpect(jsonPath("$.reservations[0].quantity").value(2))
        .andExpect(jsonPath("$.reservations[0].totalPrice").value(100000))
        .andExpect(jsonPath("$.reservations[0].status").value("PENDING_PAYMENT"))
        .andExpect(jsonPath("$.reservations[0].reservedAt").doesNotExist())
        .andExpect(
            jsonPath("$.reservations[0].paymentDeadlineAt").value("2026-12-05T12:10:00.123456"));
  }

  @Test
  @DisplayName("예약이 없으면 200과 빈 목록을 반환한다")
  void returnsEmptyListWithoutReservations() throws Exception {
    // given
    given(reservationService.getMyReservations(MEMBER_ID, Set.of())).willReturn(List.of());

    // when
    ResultActions result = get("/api/v1/reservations");

    // then
    result.andExpect(status().isOk()).andExpect(jsonPath("$.reservations.length()").value(0));
  }

  @ParameterizedTest(name = "{0}")
  @DisplayName("목록 조회 시 결제 대기가 아니면 결제 제한 시각을 null로 반환한다")
  @EnumSource(value = ReservationStatus.class, mode = EXCLUDE, names = "PENDING_PAYMENT")
  void returnsNullDeadlineInListWhenNotPending(ReservationStatus reservationStatus)
      throws Exception {
    // given
    given(reservationService.getMyReservations(MEMBER_ID, Set.of()))
        .willReturn(List.of(detail(reservationStatus)));

    // when
    ResultActions result = get("/api/v1/reservations");

    // then
    result
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reservations[0].status").value(reservationStatus.name()))
        .andExpect(jsonPath("$.reservations[0].paymentDeadlineAt").value(nullValue()));
  }

  @ParameterizedTest(name = "{0}")
  @DisplayName("status를 반복하거나 쉼표로 구분해 전달하면 해당 상태들로 조회한다")
  @ValueSource(
      strings = {
        "status=PENDING_PAYMENT&status=EXPIRED",
        "status=PENDING_PAYMENT,EXPIRED",
        "status=PENDING_PAYMENT, EXPIRED",
        "status= PENDING_PAYMENT&status=EXPIRED "
      })
  void passesStatusFilter(String query) throws Exception {
    // when
    ResultActions result = get("/api/v1/reservations?" + query);

    // then
    result.andExpect(status().isOk()).andExpect(jsonPath("$.reservations").isEmpty());
    then(reservationService)
        .should()
        .getMyReservations(
            MEMBER_ID, Set.of(ReservationStatus.PENDING_PAYMENT, ReservationStatus.EXPIRED));
  }

  @ParameterizedTest(name = "{0}")
  @DisplayName("status의 빈 값은 무시하고 나머지 상태들로 조회한다")
  @ValueSource(
      strings = {
        "status=PENDING_PAYMENT,EXPIRED,",
        "status=,PENDING_PAYMENT,,EXPIRED",
        "status=PENDING_PAYMENT&status=&status=EXPIRED"
      })
  void ignoresEmptyStatus(String query) throws Exception {
    // when
    ResultActions result = get("/api/v1/reservations?" + query);

    // then
    result.andExpect(status().isOk());
    then(reservationService)
        .should()
        .getMyReservations(
            MEMBER_ID, Set.of(ReservationStatus.PENDING_PAYMENT, ReservationStatus.EXPIRED));
  }

  @ParameterizedTest(name = "{0}")
  @DisplayName("status가 모두 빈 값이면 모든 상태의 예약을 조회한다")
  @ValueSource(strings = {"status=", "status=,", "status=&status=", "status= ,"})
  void queriesAllStatusesWhenStatusIsAllEmpty(String query) throws Exception {
    // when
    ResultActions result = get("/api/v1/reservations?" + query);

    // then
    result.andExpect(status().isOk());
    then(reservationService).should().getMyReservations(MEMBER_ID, Set.of());
  }

  @ParameterizedTest(name = "{0}")
  @DisplayName("status에 예약 상태가 아닌 값이 있으면 400을 반환한다")
  @ValueSource(
      strings = {"status=UNKNOWN", "status=pending_payment", "status=PENDING_PAYMENT,UNKNOWN"})
  void returnsBadRequestWhenStatusIsInvalid(String query) throws Exception {
    // when
    ResultActions result = get("/api/v1/reservations?" + query);

    // then
    result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    then(reservationService).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("status를 반복과 쉼표 구분을 섞어 전달하면 400을 반환한다")
  void returnsBadRequestWhenStatusFormsAreMixed() throws Exception {
    // when
    ResultActions result =
        get("/api/v1/reservations?status=PENDING_PAYMENT,EXPIRED&status=PENDING_PAYMENT");

    // then
    result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    then(reservationService).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("예약 식별자에 해당하는 예약이 있으면 200과 예약 상세를 반환한다")
  void returnsMyReservation() throws Exception {
    // given
    given(reservationService.getMyReservation(MEMBER_ID, 10L))
        .willReturn(detail(ReservationStatus.PENDING_PAYMENT));

    // when
    ResultActions result = get("/api/v1/reservations/10");

    // then
    result
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(10))
        .andExpect(jsonPath("$.performance.id").value(1))
        .andExpect(jsonPath("$.performance.title").value("공연"))
        .andExpect(jsonPath("$.performance.venue").value("공연장"))
        .andExpect(jsonPath("$.performance.performanceAt").value("2026-12-20T19:00:00"))
        .andExpect(jsonPath("$.quantity").value(2))
        .andExpect(jsonPath("$.unitPrice").value(50000))
        .andExpect(jsonPath("$.totalPrice").value(100000))
        .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
        .andExpect(jsonPath("$.reservedAt").value("2026-12-05T12:00:00.123456"))
        .andExpect(jsonPath("$.paymentDeadlineAt").value("2026-12-05T12:10:00.123456"));
  }

  @ParameterizedTest(name = "{0}")
  @DisplayName("상세 조회 시 결제 대기가 아니면 결제 제한 시각을 null로 반환한다")
  @EnumSource(value = ReservationStatus.class, mode = EXCLUDE, names = "PENDING_PAYMENT")
  void returnsNullDeadlineWhenNotPending(ReservationStatus reservationStatus) throws Exception {
    // given
    given(reservationService.getMyReservation(MEMBER_ID, 10L))
        .willReturn(detail(reservationStatus));

    // when
    ResultActions result = get("/api/v1/reservations/10");

    // then
    result
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(reservationStatus.name()))
        .andExpect(jsonPath("$.paymentDeadlineAt").value(nullValue()));
  }

  @Test
  @DisplayName("예약 식별자에 해당하는 예약이 없으면 404와 오류 코드를 반환한다")
  void returnsNotFoundWhenReservationIsMissing() throws Exception {
    // given
    given(reservationService.getMyReservation(MEMBER_ID, 10L))
        .willThrow(new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

    // when
    ResultActions result = get("/api/v1/reservations/10");

    // then
    result
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
  }

  @Test
  @DisplayName("예약 식별자에 공백이 섞이면 400을 반환한다")
  void rejectsReservationIdWithWhitespace() throws Exception {
    // when
    ResultActions result =
        mockMvc.perform(
            MockMvcRequestBuilders.get(URI.create("/api/v1/reservations/1%200"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken));

    // then
    result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    then(reservationService).shouldHaveNoInteractions();
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

  private ResultActions get(String path) throws Exception {
    return mockMvc.perform(
        MockMvcRequestBuilders.get(path)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken));
  }

  private static ReservationDetail detail(ReservationStatus status) {
    return new ReservationDetail(reservation(), performance(), status);
  }

  private static Reservation reservation() {
    LocalDateTime now = LocalDateTime.parse("2026-12-05T12:00:00.123456");
    Reservation reservation = Reservation.create(MEMBER_ID, performance(), 2, now);
    ReflectionTestUtils.setField(reservation, "id", 10L);
    return reservation;
  }

  private static Performance performance() {
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
    return performance;
  }
}
