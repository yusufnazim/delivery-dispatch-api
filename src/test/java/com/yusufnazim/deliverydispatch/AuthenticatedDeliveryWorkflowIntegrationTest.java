package com.yusufnazim.deliverydispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yusufnazim.deliverydispatch.auth.dto.LoginRequest;
import com.yusufnazim.deliverydispatch.auth.dto.LoginResponse;
import com.yusufnazim.deliverydispatch.courier.dto.UpdateCourierAvailabilityRequest;
import com.yusufnazim.deliverydispatch.courier.dto.UpdateCourierLocationRequest;
import com.yusufnazim.deliverydispatch.dispatch.dto.ManualAssignmentRequest;
import com.yusufnazim.deliverydispatch.order.dto.CreateDeliveryOrderRequest;
import com.yusufnazim.deliverydispatch.order.dto.DeliveryOrderResponse;
import com.yusufnazim.deliverydispatch.timeline.dto.DeliveryEventResponse;
import com.yusufnazim.deliverydispatch.user.CourierAvailabilityStatus;
import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import com.yusufnazim.deliverydispatch.user.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("http-workflow")
@Testcontainers
class AuthenticatedDeliveryWorkflowIntegrationTest {

    private static final String PASSWORD = "workflow-test-password";
    private static final String CURRENT_ORDER = "/api/v1/couriers/me/orders/current";
    private static final CreateDeliveryOrderRequest ORDER_REQUEST = new CreateDeliveryOrderRequest(
            "Istiklal Cd. No:1, Beyoglu", new BigDecimal("41.036900"), new BigDecimal("28.985000"),
            "Bagdat Cd. No:10, Kadikoy", new BigDecimal("40.970000"), new BigDecimal("29.057000"));

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JpaProperties jpaProperties;

    private User customer;
    private User otherCustomer;
    private User dispatcher;
    private User nearCourier;
    private User farCourier;

    @Autowired
    AuthenticatedDeliveryWorkflowIntegrationTest(
            MockMvc mockMvc, ObjectMapper objectMapper, JdbcTemplate jdbc,
            UserRepository userRepository, PasswordEncoder passwordEncoder, JpaProperties jpaProperties) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jpaProperties = jpaProperties;
    }

    @BeforeEach
    void createAccountsInMigratedDatabase() {
        assertThat(jpaProperties.getOpenInView()).isFalse();
        assertThat(AopProxyUtils.ultimateTargetClass(userRepository)).isEqualTo(SimpleJpaRepository.class);
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where not success", Long.class))
                .isZero();
        jdbc.update("delete from delivery_events");
        jdbc.update("delete from delivery_orders");
        jdbc.update("delete from users");

        String passwordHash = passwordEncoder.encode(PASSWORD);
        customer = new User("workflow-customer@example.com", passwordHash, Role.CUSTOMER);
        otherCustomer = new User("workflow-other-customer@example.com", passwordHash, Role.CUSTOMER);
        dispatcher = new User("workflow-dispatcher@example.com", passwordHash, Role.DISPATCHER);
        nearCourier = new User("workflow-near@example.com", passwordHash, Role.COURIER);
        farCourier = new User("workflow-far@example.com", passwordHash, Role.COURIER);
        userRepository.saveAllAndFlush(List.of(customer, otherCustomer, dispatcher, nearCourier, farCourier));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void realLoginTokensDriveThePersistedDeliveryLifecycle(boolean automatic) throws Exception {
        LoginResponse customerLogin = login(customer);
        LoginResponse dispatcherLogin = login(dispatcher);
        LoginResponse nearLogin = login(nearCourier);
        LoginResponse farLogin = login(farCourier);
        makeAvailable(nearLogin, "41.037000", "28.986000");
        makeAvailable(farLogin, "41.100000", "29.100000");
        LoginResponse selected = automatic ? nearLogin : farLogin;
        LoginResponse unselected = automatic ? farLogin : nearLogin;
        assertNoCurrentOrder(selected);

        long orderId = createOrder(customerLogin);
        assertPersistedOrder(orderId, null, "PENDING", "ORDER_CREATED");

        assign(orderId, dispatcherLogin, selected.userId(), automatic);
        assertPersistedOrder(orderId, selected.userId(), "ASSIGNED", "ORDER_CREATED", "COURIER_ASSIGNED");
        assertAvailability(selected.userId(), "ON_DELIVERY");
        assertAvailability(unselected.userId(), "AVAILABLE");
        assertNoCurrentOrder(unselected);

        mockMvc.perform(get("/api/v1/dispatch/orders").header(HttpHeaders.AUTHORIZATION, bearer(dispatcherLogin))
                        .param("status", "ASSIGNED").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(orderId))
                .andExpect(jsonPath("$.items[0].customerId").value(customer.getId()))
                .andExpect(jsonPath("$.items[0].courierId").value(selected.userId()))
                .andExpect(jsonPath("$.totalElements").value(1));

        // The courier discovers the id through their own endpoint, not a dispatcher response.
        DeliveryOrderResponse current = objectMapper.readValue(
                mockMvc.perform(get(CURRENT_ORDER).header(HttpHeaders.AUTHORIZATION, bearer(selected)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("ASSIGNED"))
                        .andReturn().getResponse().getContentAsString(), DeliveryOrderResponse.class);
        assertThat(current.id()).isEqualTo(orderId);
        assertThat(current.pickupAddress()).isEqualTo(ORDER_REQUEST.pickupAddress());
        assertThat(current.dropoffAddress()).isEqualTo(ORDER_REQUEST.dropoffAddress());
        assertThat(current.pickupLatitude()).isEqualByComparingTo(ORDER_REQUEST.pickupLatitude());
        assertThat(current.pickupLongitude()).isEqualByComparingTo(ORDER_REQUEST.pickupLongitude());
        assertThat(current.dropoffLatitude()).isEqualByComparingTo(ORDER_REQUEST.dropoffLatitude());
        assertThat(current.dropoffLongitude()).isEqualByComparingTo(ORDER_REQUEST.dropoffLongitude());

        mockMvc.perform(post("/api/v1/couriers/me/orders/{id}/pickup", current.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(selected)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PICKED_UP"));
        assertPersistedOrder(orderId, selected.userId(), "PICKED_UP",
                "ORDER_CREATED", "COURIER_ASSIGNED", "ORDER_PICKED_UP");
        assertAvailability(selected.userId(), "ON_DELIVERY");
        mockMvc.perform(get(CURRENT_ORDER).header(HttpHeaders.AUTHORIZATION, bearer(selected)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.status").value("PICKED_UP"));

        mockMvc.perform(post("/api/v1/couriers/me/orders/{id}/deliver", current.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(selected)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELIVERED"));
        assertPersistedOrder(orderId, selected.userId(), "DELIVERED",
                "ORDER_CREATED", "COURIER_ASSIGNED", "ORDER_PICKED_UP", "ORDER_DELIVERED");
        assertAvailability(selected.userId(), "AVAILABLE");
        assertAvailability(unselected.userId(), "AVAILABLE");
        assertNoCurrentOrder(selected);

        PersistedState completed = persistedState();
        mockMvc.perform(get("/api/v1/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, bearer(customerLogin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELIVERED"));
        mockMvc.perform(get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, bearer(customerLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(orderId))
                .andExpect(jsonPath("$.items[0].status").value("DELIVERED"))
                .andExpect(jsonPath("$.totalElements").value(1));
        List<DeliveryEventResponse> timeline = objectMapper.readValue(
                mockMvc.perform(get("/api/v1/orders/{id}/timeline", orderId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(customerLogin)))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                new TypeReference<>() {});
        assertThat(timeline).extracting(event -> event.type().name())
                .containsExactly("ORDER_CREATED", "COURIER_ASSIGNED", "ORDER_PICKED_UP", "ORDER_DELIVERED");
        assertThat(timeline).extracting(DeliveryEventResponse::description)
                .containsExactlyElementsOf(jdbc.queryForList(
                        "select description from delivery_events where delivery_order_id = ? order by created_at, id",
                        String.class, orderId));
        assertThat(timeline).extracting(DeliveryEventResponse::createdAt).doesNotContainNull().isSorted();
        assertThat(persistedState()).isEqualTo(completed);
    }

    @Test
    void invalidCredentialsAndMissingOrInvalidTokensCannotCreateOrders() throws Exception {
        PersistedState before = persistedState();
        expectError(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new LoginRequest(customer.getEmail(), "wrong-password")))),
                status().isUnauthorized(), "INVALID_LOGIN_CREDENTIALS");
        assertThat(persistedState()).isEqualTo(before);
        expectError(mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(ORDER_REQUEST))),
                status().isUnauthorized(), "AUTHENTICATION_REQUIRED");
        assertThat(persistedState()).isEqualTo(before);
        expectError(mockMvc.perform(post("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(ORDER_REQUEST))),
                status().isUnauthorized(), "AUTHENTICATION_REQUIRED");
        assertThat(persistedState()).isEqualTo(before);
    }

    @Test
    void wrongRolesCannotAssignOrdersOrPerformCourierActions() throws Exception {
        LoginResponse customerLogin = login(customer);
        LoginResponse dispatcherLogin = login(dispatcher);
        LoginResponse courierLogin = login(nearCourier);
        long orderId = createOrder(customerLogin);
        PersistedState before = persistedState();

        expectError(mockMvc.perform(post("/api/v1/dispatch/orders/{id}/auto-assign", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerLogin))),
                status().isForbidden(), "ACCESS_DENIED");
        assertThat(persistedState()).isEqualTo(before);
        expectError(mockMvc.perform(post("/api/v1/dispatch/orders/{id}/assign", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerLogin)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new ManualAssignmentRequest(nearCourier.getId())))),
                status().isForbidden(), "ACCESS_DENIED");
        assertThat(persistedState()).isEqualTo(before);
        expectError(mockMvc.perform(post("/api/v1/couriers/me/orders/{id}/pickup", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(dispatcherLogin))),
                status().isForbidden(), "ACCESS_DENIED");
        assertThat(persistedState()).isEqualTo(before);
        expectError(mockMvc.perform(post("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, bearer(courierLogin))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(ORDER_REQUEST))),
                status().isForbidden(), "ACCESS_DENIED");
        assertThat(persistedState()).isEqualTo(before);
    }

    @Test
    void otherUsersCannotReadOrChangeAnOwnedDelivery() throws Exception {
        long orderId = createOrder(login(customer));
        LoginResponse selected = login(nearCourier);
        makeAvailable(selected, "41.037000", "28.986000");
        assign(orderId, login(dispatcher), selected.userId(), true);
        LoginResponse otherCustomerLogin = login(otherCustomer);
        LoginResponse otherCourierLogin = login(farCourier);
        PersistedState before = persistedState();

        expectError(mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherCustomerLogin))),
                status().isNotFound(), "ORDER_NOT_FOUND");
        assertThat(persistedState()).isEqualTo(before);
        expectError(mockMvc.perform(get("/api/v1/orders/{id}/timeline", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherCustomerLogin))),
                status().isNotFound(), "ORDER_NOT_FOUND");
        assertThat(persistedState()).isEqualTo(before);
        expectError(mockMvc.perform(delete("/api/v1/orders/{id}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherCustomerLogin))),
                status().isNotFound(), "ORDER_NOT_FOUND");
        assertThat(persistedState()).isEqualTo(before);
        mockMvc.perform(get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, bearer(otherCustomerLogin))
                        .param("customerId", customer.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get(CURRENT_ORDER).header(HttpHeaders.AUTHORIZATION, bearer(otherCourierLogin))
                        .param("courierId", selected.userId().toString()))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(persistedState()).isEqualTo(before);
        for (String action : List.of("pickup", "deliver")) {
            expectError(mockMvc.perform(post("/api/v1/couriers/me/orders/{id}/{action}", orderId, action)
                            .header(HttpHeaders.AUTHORIZATION, bearer(otherCourierLogin))),
                    status().isNotFound(), "ORDER_NOT_FOUND");
            assertThat(persistedState()).isEqualTo(before);
        }
    }

    @Test
    void invalidTransitionsLeaveAssignmentAndTimelineUnchanged() throws Exception {
        long orderId = createOrder(login(customer));
        LoginResponse courierLogin = login(nearCourier);
        LoginResponse dispatcherLogin = login(dispatcher);
        makeAvailable(courierLogin, "41.037000", "28.986000");
        assign(orderId, dispatcherLogin, courierLogin.userId(), false);
        PersistedState before = persistedState();

        expectError(mockMvc.perform(post("/api/v1/couriers/me/orders/{id}/deliver", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(courierLogin))),
                status().isConflict(), "INVALID_ORDER_STATUS_TRANSITION");
        assertThat(persistedState()).isEqualTo(before);
        expectError(mockMvc.perform(patch("/api/v1/couriers/me/availability")
                        .header(HttpHeaders.AUTHORIZATION, bearer(courierLogin)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new UpdateCourierAvailabilityRequest(CourierAvailabilityStatus.AVAILABLE)))),
                status().isConflict(), "COURIER_AVAILABILITY_CHANGE_NOT_ALLOWED");
        assertThat(persistedState()).isEqualTo(before);
        expectError(mockMvc.perform(post("/api/v1/dispatch/orders/{id}/auto-assign", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(dispatcherLogin))),
                status().isConflict(), "ORDER_ASSIGNMENT_NOT_ALLOWED");
        assertThat(persistedState()).isEqualTo(before);
    }

    private LoginResponse login(User user) throws Exception {
        LoginResponse response = objectMapper.readValue(
                mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(new LoginRequest(user.getEmail(), PASSWORD))))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), LoginResponse.class);
        assertThat(response.token()).isNotBlank();
        assertThat(response.userId()).isEqualTo(user.getId());
        assertThat(response.email()).isEqualTo(user.getEmail());
        assertThat(response.role()).isEqualTo(user.getRole());
        return response;
    }

    private String bearer(LoginResponse login) {
        return "Bearer " + login.token();
    }

    private void makeAvailable(LoginResponse login, String latitude, String longitude) throws Exception {
        mockMvc.perform(patch("/api/v1/couriers/me/location").header(HttpHeaders.AUTHORIZATION, bearer(login))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(
                                new UpdateCourierLocationRequest(new BigDecimal(latitude), new BigDecimal(longitude)))))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/couriers/me/availability").header(HttpHeaders.AUTHORIZATION, bearer(login))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(
                                new UpdateCourierAvailabilityRequest(CourierAvailabilityStatus.AVAILABLE))))
                .andExpect(status().isOk());
    }

    private long createOrder(LoginResponse login) throws Exception {
        DeliveryOrderResponse response = objectMapper.readValue(
                mockMvc.perform(post("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, bearer(login))
                                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(ORDER_REQUEST)))
                        .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"))
                        .andReturn().getResponse().getContentAsString(), DeliveryOrderResponse.class);
        assertThat(response.id()).isPositive();
        return response.id();
    }

    private void assign(long orderId, LoginResponse login, long courierId, boolean automatic) throws Exception {
        var request = post("/api/v1/dispatch/orders/{id}/" + (automatic ? "auto-assign" : "assign"), orderId)
                .header(HttpHeaders.AUTHORIZATION, bearer(login));
        if (!automatic) {
            request.contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(new ManualAssignmentRequest(courierId)));
        }
        mockMvc.perform(request).andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.courierId").value(courierId))
                .andExpect(jsonPath("$.status").value("ASSIGNED"));
    }

    private void assertNoCurrentOrder(LoginResponse login) throws Exception {
        mockMvc.perform(get(CURRENT_ORDER).header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
    }

    private void assertPersistedOrder(long orderId, Long courierId, String status, String... events) {
        assertThat(jdbc.queryForMap("select customer_id, courier_id, status from delivery_orders where id = ?", orderId))
                .containsEntry("customer_id", customer.getId()).containsEntry("courier_id", courierId)
                .containsEntry("status", status);
        assertThat(jdbc.queryForList(
                "select event_type from delivery_events where delivery_order_id = ? order by created_at, id", String.class, orderId))
                .containsExactly(events);
    }

    private void assertAvailability(long courierId, String availability) {
        assertThat(jdbc.queryForObject("select courier_availability_status from users where id = ?", String.class, courierId))
                .isEqualTo(availability);
    }

    private void expectError(ResultActions result, ResultMatcher expectedStatus, String code) throws Exception {
        result.andExpect(expectedStatus).andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.details").isEmpty());
    }

    // No test transaction: JDBC reads observe writes committed by each HTTP request.
    private PersistedState persistedState() {
        return new PersistedState(
                jdbc.queryForList("select * from delivery_orders order by id"),
                jdbc.queryForList("""
                        select id, version, courier_availability_status, courier_latitude, courier_longitude, updated_at
                        from users order by id
                        """),
                jdbc.queryForList("select * from delivery_events order by id"));
    }

    private record PersistedState(
            List<Map<String, Object>> orders, List<Map<String, Object>> users, List<Map<String, Object>> events) {}
}
