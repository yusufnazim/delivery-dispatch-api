package com.yusufnazim.deliverydispatch.timeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.yusufnazim.deliverydispatch.order.DeliveryOrder;
import com.yusufnazim.deliverydispatch.order.DeliveryOrderRepository;
import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import com.yusufnazim.deliverydispatch.user.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DeliveryEventRepositoryTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private DeliveryEventRepository deliveryEventRepository;

    @Autowired
    private DeliveryOrderRepository deliveryOrderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void savesDeliveryEventsAndFindsOrderTimeline() {
        DeliveryOrder order = deliveryOrderRepository.save(orderFor("timeline-customer@example.com"));
        DeliveryOrder otherOrder = deliveryOrderRepository.save(orderFor("other-timeline-customer@example.com"));
        DeliveryEvent created = deliveryEventRepository.save(new DeliveryEvent(
                order,
                DeliveryEventType.ORDER_CREATED,
                "Order created"));
        DeliveryEvent assigned = deliveryEventRepository.save(new DeliveryEvent(
                order,
                DeliveryEventType.COURIER_ASSIGNED,
                "Courier assigned"));
        deliveryEventRepository.save(new DeliveryEvent(
                otherOrder,
                DeliveryEventType.ORDER_CREATED,
                "Other order created"));
        deliveryEventRepository.flush();
        entityManager.clear();

        List<DeliveryEvent> timeline = deliveryEventRepository
                .findByDeliveryOrderIdOrderByCreatedAtAscIdAsc(order.getId());

        assertThat(timeline)
                .extracting(DeliveryEvent::getId)
                .containsExactly(created.getId(), assigned.getId());
        assertThat(timeline)
                .extracting(DeliveryEvent::getEventType)
                .containsExactly(DeliveryEventType.ORDER_CREATED, DeliveryEventType.COURIER_ASSIGNED);
        assertThat(timeline.getFirst().getDeliveryOrder().getId()).isEqualTo(order.getId());
        assertThat(timeline.getFirst().getDescription()).isEqualTo("Order created");
        assertThat(timeline.getFirst().getCreatedAt()).isNotNull();
    }

    private DeliveryOrder orderFor(String customerEmail) {
        User customer = userRepository.save(new User(
                customerEmail,
                "hashed-password",
                Role.CUSTOMER));
        return new DeliveryOrder(
                customer,
                "Istiklal Cd. No:1, Beyoglu",
                new BigDecimal("41.036900"),
                new BigDecimal("28.985000"),
                "Bagdat Cd. No:10, Kadikoy",
                new BigDecimal("40.970000"),
                new BigDecimal("29.057000"));
    }
}
