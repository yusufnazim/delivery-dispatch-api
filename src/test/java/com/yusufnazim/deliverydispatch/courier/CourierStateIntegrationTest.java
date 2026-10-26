package com.yusufnazim.deliverydispatch.courier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yusufnazim.deliverydispatch.courier.exception.CourierAvailabilityChangeNotAllowedException;
import com.yusufnazim.deliverydispatch.courier.exception.CourierUpdateConflictException;
import com.yusufnazim.deliverydispatch.dispatch.DispatchService;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
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
class CourierStateIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final BigDecimal ORIGINAL_LATITUDE = new BigDecimal("41.037000");
    private static final BigDecimal ORIGINAL_LONGITUDE = new BigDecimal("28.986000");
    private static final BigDecimal NEW_LATITUDE = new BigDecimal("41.008200");
    private static final BigDecimal NEW_LONGITUDE = new BigDecimal("28.978400");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private final CourierService courierService;
    private final DispatchService dispatchService;
    private final DeliveryOrderService orderService;
    private final UserRepository userRepository;
    private final DeliveryOrderRepository orderRepository;
    private final DeliveryEventRepository eventRepository;

    private Long courierId;
    private Long orderId;

    @Autowired
    CourierStateIntegrationTest(
            CourierService courierService,
            DispatchService dispatchService,
            DeliveryOrderService orderService,
            UserRepository userRepository,
            DeliveryOrderRepository orderRepository,
            DeliveryEventRepository eventRepository) {
        this.courierService = courierService;
        this.dispatchService = dispatchService;
        this.orderService = orderService;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
    }

    @BeforeEach
    void createPendingOrderAndAvailableCourier() {
        eventRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        Long customerId = userRepository.save(new User("state-customer@example.com", "hash", Role.CUSTOMER)).getId();
        User courier = new User("state-courier@example.com", "hash", Role.COURIER);
        courier.updateCourierAvailabilityStatus(CourierAvailabilityStatus.AVAILABLE);
        courier.updateCourierLocation(ORIGINAL_LATITUDE, ORIGINAL_LONGITUDE);
        courierId = userRepository.save(courier).getId();
        orderId = orderService.createOrder(customerId, new CreateDeliveryOrderRequest(
                "Pickup", new BigDecimal("41.036900"), new BigDecimal("28.985000"),
                "Dropoff", new BigDecimal("40.970000"), new BigDecimal("29.057000"))).id();
    }

    @ParameterizedTest
    @CsvSource({"ASSIGNED, AVAILABLE", "ASSIGNED, UNAVAILABLE", "PICKED_UP, AVAILABLE", "PICKED_UP, UNAVAILABLE"})
    void activeDeliveryRejectsManualAvailabilityWithoutChangingPersistedState(
            OrderStatus status, CourierAvailabilityStatus requested) {
        prepareActiveDelivery(status);
        User beforeCourier = courier();
        DeliveryOrder beforeOrder = order();
        var beforeEvents = eventRepository.findByDeliveryOrderIdOrderByCreatedAtAscIdAsc(orderId);

        assertThatThrownBy(() -> courierService.updateAvailability(courierId, requested))
                .isInstanceOf(CourierAvailabilityChangeNotAllowedException.class);

        assertThat(courier().getCourierAvailabilityStatus()).isEqualTo(CourierAvailabilityStatus.ON_DELIVERY);
        assertThat(courier().getVersion()).isEqualTo(beforeCourier.getVersion());
        assertThat(courier().getUpdatedAt()).isEqualTo(beforeCourier.getUpdatedAt());
        assertThat(order().getStatus()).isEqualTo(status);
        assertThat(order().getCourier().getId()).isEqualTo(courierId);
        assertThat(order().getVersion()).isEqualTo(beforeOrder.getVersion());
        assertThat(eventRepository.findByDeliveryOrderIdOrderByCreatedAtAscIdAsc(orderId))
                .extracting(DeliveryEvent::getId).containsExactlyElementsOf(
                        beforeEvents.stream().map(DeliveryEvent::getId).toList());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"ASSIGNED", "PICKED_UP"})
    void activeOrderGuardDoesNotTrustInconsistentAvailability(OrderStatus status) {
        prepareActiveDelivery(status);
        User inconsistent = courier();
        inconsistent.updateCourierAvailabilityStatus(CourierAvailabilityStatus.AVAILABLE);
        userRepository.saveAndFlush(inconsistent);

        assertThatThrownBy(() -> courierService.updateAvailability(courierId, CourierAvailabilityStatus.UNAVAILABLE))
                .isInstanceOf(CourierAvailabilityChangeNotAllowedException.class);

        assertThat(courier().getCourierAvailabilityStatus()).isEqualTo(CourierAvailabilityStatus.AVAILABLE);
        assertThat(order().getStatus()).isEqualTo(status);
    }

    @Test
    void idleCourierCanChangeAvailabilityIncludingAnUnchangedValue() {
        courierService.updateAvailability(courierId, CourierAvailabilityStatus.UNAVAILABLE);
        assertThat(courier().getCourierAvailabilityStatus()).isEqualTo(CourierAvailabilityStatus.UNAVAILABLE);

        courierService.updateAvailability(courierId, CourierAvailabilityStatus.AVAILABLE);
        courierService.updateAvailability(courierId, CourierAvailabilityStatus.AVAILABLE);
        assertThat(courier().getCourierAvailabilityStatus()).isEqualTo(CourierAvailabilityStatus.AVAILABLE);
        assertTimeline(DeliveryEventType.ORDER_CREATED);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"ASSIGNED", "PICKED_UP"})
    void locationUpdatesKeepActiveDeliveryState(OrderStatus status) {
        prepareActiveDelivery(status);
        long eventCount = eventRepository.count();

        updateLocation();

        assertLocation(NEW_LATITUDE, NEW_LONGITUDE);
        assertThat(courier().getCourierAvailabilityStatus()).isEqualTo(CourierAvailabilityStatus.ON_DELIVERY);
        assertThat(order().getStatus()).isEqualTo(status);
        assertThat(eventRepository.count()).isEqualTo(eventCount);
    }

    @Test
    void completionReleasesCourierAndAllowsManualAvailabilityAgain() {
        prepareActiveDelivery(OrderStatus.PICKED_UP);

        courierService.deliverOrder(courierId, orderId);

        assertThat(order().getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(courier().getCourierAvailabilityStatus()).isEqualTo(CourierAvailabilityStatus.AVAILABLE);
        courierService.updateAvailability(courierId, CourierAvailabilityStatus.UNAVAILABLE);
        assertThat(courier().getCourierAvailabilityStatus()).isEqualTo(CourierAvailabilityStatus.UNAVAILABLE);
        assertTimeline(DeliveryEventType.ORDER_CREATED, DeliveryEventType.COURIER_ASSIGNED,
                DeliveryEventType.ORDER_PICKED_UP, DeliveryEventType.ORDER_DELIVERED);
    }

    @ParameterizedTest
    @CsvSource({"AVAILABILITY, false", "AVAILABILITY, true", "LOCATION, false", "LOCATION, true"})
    void staleCourierUpdateCannotOverwriteCommittedAssignment(CourierWrite write, boolean automatic) throws Exception {
        assertStaleWriteRejected(userRepository, () -> updateCourier(write), () -> assign(automatic),
                CourierUpdateConflictException.class);

        assertThat(order().getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(order().getCourier().getId()).isEqualTo(courierId);
        assertThat(courier().getCourierAvailabilityStatus()).isEqualTo(CourierAvailabilityStatus.ON_DELIVERY);
        assertLocation(ORIGINAL_LATITUDE, ORIGINAL_LONGITUDE);
        assertTimeline(DeliveryEventType.ORDER_CREATED, DeliveryEventType.COURIER_ASSIGNED);
    }

    @ParameterizedTest
    @CsvSource({"AVAILABILITY, false", "AVAILABILITY, true", "LOCATION, false", "LOCATION, true"})
    void staleAssignmentRollsBackWhenCourierUpdateCommitsFirst(CourierWrite write, boolean automatic) throws Exception {
        assertStaleWriteRejected(orderRepository, () -> assign(automatic), () -> updateCourier(write),
                OrderAssignmentConflictException.class);

        assertThat(order().getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order().getVersion()).isZero();
        assertThat(order().getCourier()).isNull();
        assertCourierWritePersisted(write);
        assertTimeline(DeliveryEventType.ORDER_CREATED);
    }

    @ParameterizedTest
    @EnumSource(CourierWrite.class)
    void staleIdleUpdateDoesNotOverwriteAnotherCourierUpdate(CourierWrite stale) throws Exception {
        CourierWrite winner = stale == CourierWrite.LOCATION ? CourierWrite.AVAILABILITY : CourierWrite.LOCATION;

        assertStaleWriteRejected(userRepository, () -> updateCourier(stale), () -> updateCourier(winner),
                CourierUpdateConflictException.class);

        assertCourierWritePersisted(winner);
        assertThat(order().getStatus()).isEqualTo(OrderStatus.PENDING);
        assertTimeline(DeliveryEventType.ORDER_CREATED);
    }

    @Test
    void staleLocationCannotRestoreOnDeliveryAfterCompletion() throws Exception {
        prepareActiveDelivery(OrderStatus.PICKED_UP);

        assertStaleWriteRejected(userRepository, this::updateLocation,
                () -> courierService.deliverOrder(courierId, orderId), CourierUpdateConflictException.class);

        assertThat(order().getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(courier().getCourierAvailabilityStatus()).isEqualTo(CourierAvailabilityStatus.AVAILABLE);
        assertLocation(ORIGINAL_LATITUDE, ORIGINAL_LONGITUDE);
        assertTimeline(DeliveryEventType.ORDER_CREATED, DeliveryEventType.COURIER_ASSIGNED,
                DeliveryEventType.ORDER_PICKED_UP, DeliveryEventType.ORDER_DELIVERED);
    }

    @Test
    void staleCompletionRollsBackOrderAndTimelineWhenLocationCommitsFirst() throws Exception {
        prepareActiveDelivery(OrderStatus.PICKED_UP);
        Long orderVersion = order().getVersion();

        assertStaleWriteRejected(userRepository, () -> courierService.deliverOrder(courierId, orderId),
                this::updateLocation, CourierUpdateConflictException.class);

        assertThat(order().getStatus()).isEqualTo(OrderStatus.PICKED_UP);
        assertThat(order().getVersion()).isEqualTo(orderVersion);
        assertThat(courier().getCourierAvailabilityStatus()).isEqualTo(CourierAvailabilityStatus.ON_DELIVERY);
        assertLocation(NEW_LATITUDE, NEW_LONGITUDE);
        assertTimeline(DeliveryEventType.ORDER_CREATED, DeliveryEventType.COURIER_ASSIGNED,
                DeliveryEventType.ORDER_PICKED_UP);

        courierService.deliverOrder(courierId, orderId);
        assertThat(order().getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(courier().getCourierAvailabilityStatus()).isEqualTo(CourierAvailabilityStatus.AVAILABLE);
        assertLocation(NEW_LATITUDE, NEW_LONGITUDE);
        assertTimeline(DeliveryEventType.ORDER_CREATED, DeliveryEventType.COURIER_ASSIGNED,
                DeliveryEventType.ORDER_PICKED_UP, DeliveryEventType.ORDER_DELIVERED);
    }

    @Test
    void concurrentCompletionsPersistOnlyOneDeliveryEvent() throws Exception {
        prepareActiveDelivery(OrderStatus.PICKED_UP);
        Runnable completion = () -> courierService.deliverOrder(courierId, orderId);

        assertStaleWriteRejected(userRepository, completion, completion, CourierUpdateConflictException.class);

        assertThat(order().getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(courier().getCourierAvailabilityStatus()).isEqualTo(CourierAvailabilityStatus.AVAILABLE);
        assertTimeline(DeliveryEventType.ORDER_CREATED, DeliveryEventType.COURIER_ASSIGNED,
                DeliveryEventType.ORDER_PICKED_UP, DeliveryEventType.ORDER_DELIVERED);
    }

    private void assertStaleWriteRejected(
            Object repository, Runnable staleWrite, Runnable winningWrite,
            Class<? extends RuntimeException> expectedConflict) throws Exception {
        CountDownLatch readCompleted = new CountDownLatch(1);
        CountDownLatch winnerCommitted = new CountDownLatch(1);
        AtomicReference<Thread> staleThread = new AtomicReference<>();
        // Pause one real transaction after its reads, then commit the competing write before its flush.
        MethodInterceptor pauseStaleFlush = invocation -> {
            if (Thread.currentThread() == staleThread.get() && invocation.getMethod().getName().equals("flush")) {
                readCompleted.countDown();
                assertThat(winnerCommitted.await(15, TimeUnit.SECONDS)).isTrue();
            }
            return invocation.proceed();
        };
        Advised proxy = (Advised) repository;
        proxy.addAdvice(0, pauseStaleFlush);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var result = executor.submit(() -> {
                staleThread.set(Thread.currentThread());
                assertThatThrownBy(staleWrite::run).isInstanceOf(expectedConflict);
            });
            assertThat(readCompleted.await(15, TimeUnit.SECONDS)).isTrue();
            winningWrite.run();
            winnerCommitted.countDown();
            result.get(30, TimeUnit.SECONDS);
        } finally {
            winnerCommitted.countDown();
            executor.shutdownNow();
            try {
                assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                proxy.removeAdvice(pauseStaleFlush);
            }
        }
    }

    private void prepareActiveDelivery(OrderStatus status) {
        assign(false);
        if (status == OrderStatus.PICKED_UP) {
            courierService.pickupOrder(courierId, orderId);
        }
    }

    private void assign(boolean automatic) {
        if (automatic) {
            dispatchService.autoAssignOrder(orderId);
        } else {
            dispatchService.manualAssignOrder(orderId, courierId);
        }
    }

    private void updateCourier(CourierWrite write) {
        if (write == CourierWrite.AVAILABILITY) {
            courierService.updateAvailability(courierId, CourierAvailabilityStatus.UNAVAILABLE);
        } else {
            updateLocation();
        }
    }

    private void updateLocation() {
        courierService.updateLocation(courierId, NEW_LATITUDE, NEW_LONGITUDE);
    }

    private void assertCourierWritePersisted(CourierWrite write) {
        assertThat(courier().getCourierAvailabilityStatus()).isEqualTo(
                write == CourierWrite.AVAILABILITY ? CourierAvailabilityStatus.UNAVAILABLE : CourierAvailabilityStatus.AVAILABLE);
        assertLocation(write == CourierWrite.LOCATION ? NEW_LATITUDE : ORIGINAL_LATITUDE,
                write == CourierWrite.LOCATION ? NEW_LONGITUDE : ORIGINAL_LONGITUDE);
    }

    private User courier() {
        return userRepository.findById(courierId).orElseThrow();
    }

    private DeliveryOrder order() {
        return orderRepository.findById(orderId).orElseThrow();
    }

    private void assertLocation(BigDecimal latitude, BigDecimal longitude) {
        User courier = courier();
        assertThat(courier.getCourierLatitude()).isEqualByComparingTo(latitude);
        assertThat(courier.getCourierLongitude()).isEqualByComparingTo(longitude);
    }

    private void assertTimeline(DeliveryEventType... expectedTypes) {
        assertThat(eventRepository.findByDeliveryOrderIdOrderByCreatedAtAscIdAsc(orderId))
                .extracting(DeliveryEvent::getEventType).containsExactly(expectedTypes);
    }

    private enum CourierWrite { AVAILABILITY, LOCATION }
}
