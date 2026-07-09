package com.yusufnazim.deliverydispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.yusufnazim.deliverydispatch.courier.CourierService;
import com.yusufnazim.deliverydispatch.dispatch.DispatchService;
import com.yusufnazim.deliverydispatch.order.DeliveryOrder;
import com.yusufnazim.deliverydispatch.order.DeliveryOrderRepository;
import com.yusufnazim.deliverydispatch.order.DeliveryOrderService;
import com.yusufnazim.deliverydispatch.order.OrderStatus;
import com.yusufnazim.deliverydispatch.order.dto.CreateDeliveryOrderRequest;
import com.yusufnazim.deliverydispatch.order.dto.DeliveryOrderResponse;
import com.yusufnazim.deliverydispatch.timeline.DeliveryEvent;
import com.yusufnazim.deliverydispatch.timeline.DeliveryEventRepository;
import com.yusufnazim.deliverydispatch.timeline.DeliveryEventType;
import com.yusufnazim.deliverydispatch.user.CourierAvailabilityStatus;
import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import com.yusufnazim.deliverydispatch.user.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
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
        "spring.main.allow-bean-definition-overriding=true"
})
@Testcontainers(disabledWithoutDocker = true)
class DeliveryWorkflowIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private final DeliveryOrderService deliveryOrderService;
    private final DispatchService dispatchService;
    private final CourierService courierService;
    private final DeliveryOrderRepository deliveryOrderRepository;
    private final DeliveryEventRepository deliveryEventRepository;
    private final UserRepository userRepository;

    @Autowired
    DeliveryWorkflowIntegrationTest(
            DeliveryOrderService deliveryOrderService,
            DispatchService dispatchService,
            CourierService courierService,
            DeliveryOrderRepository deliveryOrderRepository,
            DeliveryEventRepository deliveryEventRepository,
            UserRepository userRepository) {
        this.deliveryOrderService = deliveryOrderService;
        this.dispatchService = dispatchService;
        this.courierService = courierService;
        this.deliveryOrderRepository = deliveryOrderRepository;
        this.deliveryEventRepository = deliveryEventRepository;
        this.userRepository = userRepository;
    }

    @Test
    void customerOrderMovesThroughPersistedDeliveryWorkflow() {
        User customer = userRepository.save(new User(
                "workflow-customer@example.com",
                "hashed-password",
                Role.CUSTOMER));
        User nearCourier = userRepository.save(availableCourier(
                "workflow-near-courier@example.com",
                "41.037000",
                "28.986000"));
        User farCourier = userRepository.save(availableCourier(
                "workflow-far-courier@example.com",
                "41.100000",
                "29.100000"));

        DeliveryOrderResponse createdOrder = deliveryOrderService.createOrder(
                customer.getId(),
                createOrderRequest());

        DeliveryOrder assignedOrder = dispatchService.assignNearestEligibleCourier(createdOrder.id());
        DeliveryOrderResponse pickedUpOrder = courierService.pickupOrder(nearCourier.getId(), createdOrder.id());
        DeliveryOrderResponse deliveredOrder = courierService.deliverOrder(nearCourier.getId(), createdOrder.id());

        DeliveryOrder persistedOrder = deliveryOrderRepository
                .findByIdAndCourierId(createdOrder.id(), nearCourier.getId())
                .orElseThrow();
        User persistedNearCourier = userRepository.findById(nearCourier.getId()).orElseThrow();
        User persistedFarCourier = userRepository.findById(farCourier.getId()).orElseThrow();
        List<DeliveryEvent> timeline = deliveryEventRepository
                .findByDeliveryOrderIdOrderByCreatedAtAscIdAsc(createdOrder.id());

        assertThat(createdOrder.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(assignedOrder.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(pickedUpOrder.status()).isEqualTo(OrderStatus.PICKED_UP);
        assertThat(deliveredOrder.status()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(persistedOrder.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(persistedNearCourier.getCourierAvailabilityStatus())
                .isEqualTo(CourierAvailabilityStatus.AVAILABLE);
        assertThat(persistedFarCourier.getCourierAvailabilityStatus())
                .isEqualTo(CourierAvailabilityStatus.AVAILABLE);
        assertThat(timeline)
                .extracting(DeliveryEvent::getEventType)
                .containsExactly(
                        DeliveryEventType.ORDER_CREATED,
                        DeliveryEventType.COURIER_ASSIGNED,
                        DeliveryEventType.ORDER_PICKED_UP,
                        DeliveryEventType.ORDER_DELIVERED);
        assertThat(timeline)
                .extracting(DeliveryEvent::getDescription)
                .containsExactly(
                        "Order created",
                        "Courier assigned",
                        "Order picked up",
                        "Order delivered");
        assertThat(timeline)
                .allSatisfy(event -> assertThat(event.getCreatedAt()).isNotNull());
    }

    private User availableCourier(String email, String latitude, String longitude) {
        User courier = new User(email, "hashed-password", Role.COURIER);
        courier.updateCourierAvailabilityStatus(CourierAvailabilityStatus.AVAILABLE);
        courier.updateCourierLocation(new BigDecimal(latitude), new BigDecimal(longitude));
        return courier;
    }

    private CreateDeliveryOrderRequest createOrderRequest() {
        return new CreateDeliveryOrderRequest(
                "Istiklal Cd. No:1, Beyoglu",
                new BigDecimal("41.036900"),
                new BigDecimal("28.985000"),
                "Bagdat Cd. No:10, Kadikoy",
                new BigDecimal("40.970000"),
                new BigDecimal("29.057000"));
    }
}
