package com.yusufnazim.deliverydispatch.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.yusufnazim.deliverydispatch.courier.CourierService;
import com.yusufnazim.deliverydispatch.dispatch.dto.DispatchAssignmentResponse;
import com.yusufnazim.deliverydispatch.order.DeliveryOrder;
import com.yusufnazim.deliverydispatch.order.DeliveryOrderRepository;
import com.yusufnazim.deliverydispatch.order.DeliveryOrderService;
import com.yusufnazim.deliverydispatch.order.OrderStatus;
import com.yusufnazim.deliverydispatch.order.dto.CreateDeliveryOrderRequest;
import com.yusufnazim.deliverydispatch.order.exception.OrderAssignmentConflictException;
import com.yusufnazim.deliverydispatch.timeline.DeliveryEvent;
import com.yusufnazim.deliverydispatch.timeline.DeliveryEventRepository;
import com.yusufnazim.deliverydispatch.timeline.DeliveryEventType;
import com.yusufnazim.deliverydispatch.user.CourierAvailabilityStatus;
import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import com.yusufnazim.deliverydispatch.user.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.main.allow-bean-definition-overriding=true"
})
@Testcontainers
class DispatchConcurrencyIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private final DispatchService dispatchService;
    private final CourierService courierService;
    private final DeliveryOrderService orderService;
    private final DeliveryOrderRepository orderRepository;
    private final DeliveryEventRepository eventRepository;
    private final UserRepository userRepository;

    private Long customerId;

    @Autowired
    DispatchConcurrencyIntegrationTest(
            DispatchService dispatchService,
            CourierService courierService,
            DeliveryOrderService orderService,
            DeliveryOrderRepository orderRepository,
            DeliveryEventRepository eventRepository,
            UserRepository userRepository) {
        this.dispatchService = dispatchService;
        this.courierService = courierService;
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
    }

    @BeforeEach
    void createCustomer() {
        eventRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        customerId = userRepository.save(new User("race-customer@example.com", "hash", Role.CUSTOMER)).getId();
    }

    @ParameterizedTest
    @CsvSource({"MANUAL, MANUAL", "AUTO, AUTO", "MANUAL, AUTO", "AUTO, MANUAL"})
    void differentOrdersCannotClaimTheSameCourier(AssignmentMode firstMode, AssignmentMode secondMode)
            throws Exception {
        Long courierId = createCourier("race-courier@example.com");
        Long firstOrderId = createOrder();
        Long secondOrderId = createOrder();

        DispatchAssignmentResponse winner = race(
                () -> assign(firstMode, firstOrderId, courierId),
                () -> assign(secondMode, secondOrderId, courierId));

        Long loserId = winner.orderId().equals(firstOrderId) ? secondOrderId : firstOrderId;
        DeliveryOrder assigned = orderRepository.findById(winner.orderId()).orElseThrow();
        DeliveryOrder pending = orderRepository.findById(loserId).orElseThrow();
        assertThat(assigned.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(assigned.getCourier().getId()).isEqualTo(courierId);
        assertThat(pending.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(pending.getCourier()).isNull();
        assertThat(pending.getVersion()).isZero();
        assertThat(userRepository.findById(courierId).orElseThrow().getCourierAvailabilityStatus())
                .isEqualTo(CourierAvailabilityStatus.ON_DELIVERY);
        assertTimeline(winner.orderId(), DeliveryEventType.ORDER_CREATED, DeliveryEventType.COURIER_ASSIGNED);
        assertTimeline(loserId, DeliveryEventType.ORDER_CREATED);
    }

    @Test
    void sameOrderCannotBeAssignedToDifferentCouriers() throws Exception {
        Long firstCourierId = createCourier("first-race-courier@example.com");
        Long secondCourierId = createCourier("second-race-courier@example.com");
        Long orderId = createOrder();

        DispatchAssignmentResponse winner = race(
                () -> dispatchService.manualAssignOrder(orderId, firstCourierId),
                () -> dispatchService.manualAssignOrder(orderId, secondCourierId));

        Long loserId = winner.courierId().equals(firstCourierId) ? secondCourierId : firstCourierId;
        DeliveryOrder assigned = orderRepository.findById(orderId).orElseThrow();
        assertThat(assigned.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(assigned.getCourier().getId()).isEqualTo(winner.courierId());
        assertThat(userRepository.findById(winner.courierId()).orElseThrow().getCourierAvailabilityStatus())
                .isEqualTo(CourierAvailabilityStatus.ON_DELIVERY);
        assertThat(userRepository.findById(loserId).orElseThrow().getCourierAvailabilityStatus())
                .isEqualTo(CourierAvailabilityStatus.AVAILABLE);
        assertTimeline(orderId, DeliveryEventType.ORDER_CREATED, DeliveryEventType.COURIER_ASSIGNED);
    }

    @Test
    void courierCanBeAssignedAgainAfterCompletingDelivery() {
        Long courierId = createCourier("reusable-courier@example.com");
        Long firstOrderId = createOrder();
        Long secondOrderId = createOrder();

        dispatchService.manualAssignOrder(firstOrderId, courierId);
        courierService.pickupOrder(courierId, firstOrderId);
        courierService.deliverOrder(courierId, firstOrderId);
        DispatchAssignmentResponse secondAssignment = dispatchService.autoAssignOrder(secondOrderId);

        assertThat(secondAssignment.courierId()).isEqualTo(courierId);
        assertThat(orderRepository.findById(firstOrderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.DELIVERED);
        assertThat(orderRepository.findById(secondOrderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.ASSIGNED);
        assertTimeline(firstOrderId, DeliveryEventType.ORDER_CREATED, DeliveryEventType.COURIER_ASSIGNED,
                DeliveryEventType.ORDER_PICKED_UP, DeliveryEventType.ORDER_DELIVERED);
        assertTimeline(secondOrderId, DeliveryEventType.ORDER_CREATED, DeliveryEventType.COURIER_ASSIGNED);
    }

    private DispatchAssignmentResponse race(
            Callable<DispatchAssignmentResponse> first,
            Callable<DispatchAssignmentResponse> second) throws Exception {
        CyclicBarrier beforeFlush = new CyclicBarrier(2);
        // Both real transactions finish eligibility reads before either writes to PostgreSQL.
        MethodInterceptor synchronizeWrites = invocation -> {
            if (invocation.getMethod().getName().equals("flush")) {
                beforeFlush.await(15, TimeUnit.SECONDS);
            }
            return invocation.proceed();
        };
        Advised repositoryProxy = (Advised) orderRepository;
        repositoryProxy.addAdvice(0, synchronizeWrites);
        List<AssignmentOutcome> outcomes;
        var executor = Executors.newFixedThreadPool(2);
        try {
            var firstResult = executor.submit(() -> attempt(first));
            var secondResult = executor.submit(() -> attempt(second));
            outcomes = List.of(firstResult.get(30, TimeUnit.SECONDS), secondResult.get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            try {
                assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                repositoryProxy.removeAdvice(synchronizeWrites);
            }
        }

        assertThat(outcomes).filteredOn(outcome -> outcome.conflict() != null).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> outcome.response() != null).hasSize(1);
        return outcomes.stream().map(AssignmentOutcome::response).filter(java.util.Objects::nonNull)
                .findFirst().orElseThrow();
    }

    private AssignmentOutcome attempt(Callable<DispatchAssignmentResponse> assignment) throws Exception {
        try {
            return new AssignmentOutcome(assignment.call(), null);
        } catch (OrderAssignmentConflictException conflict) {
            return new AssignmentOutcome(null, conflict);
        }
    }

    private DispatchAssignmentResponse assign(AssignmentMode mode, Long orderId, Long courierId) {
        return mode == AssignmentMode.AUTO
                ? dispatchService.autoAssignOrder(orderId)
                : dispatchService.manualAssignOrder(orderId, courierId);
    }

    private Long createCourier(String email) {
        User courier = new User(email, "hash", Role.COURIER);
        courier.updateCourierAvailabilityStatus(CourierAvailabilityStatus.AVAILABLE);
        courier.updateCourierLocation(new BigDecimal("41.037000"), new BigDecimal("28.986000"));
        return userRepository.save(courier).getId();
    }

    private Long createOrder() {
        return orderService.createOrder(customerId, new CreateDeliveryOrderRequest(
                "Pickup", new BigDecimal("41.036900"), new BigDecimal("28.985000"),
                "Dropoff", new BigDecimal("40.970000"), new BigDecimal("29.057000"))).id();
    }

    private void assertTimeline(Long orderId, DeliveryEventType... expectedTypes) {
        assertThat(eventRepository.findByDeliveryOrderIdOrderByCreatedAtAscIdAsc(orderId))
                .extracting(DeliveryEvent::getEventType).containsExactly(expectedTypes);
    }

    private enum AssignmentMode { MANUAL, AUTO }

    private record AssignmentOutcome(DispatchAssignmentResponse response, OrderAssignmentConflictException conflict) {
    }
}
