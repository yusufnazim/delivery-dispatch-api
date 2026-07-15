package com.yusufnazim.deliverydispatch.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.yusufnazim.deliverydispatch.order.DeliveryOrder;
import com.yusufnazim.deliverydispatch.order.DeliveryOrderRepository;
import com.yusufnazim.deliverydispatch.order.OrderStatus;
import com.yusufnazim.deliverydispatch.timeline.DeliveryEventRepository;
import com.yusufnazim.deliverydispatch.timeline.DeliveryEventType;
import com.yusufnazim.deliverydispatch.timeline.DeliveryTimelineService;
import com.yusufnazim.deliverydispatch.user.CourierAvailabilityStatus;
import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import com.yusufnazim.deliverydispatch.user.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.demo-data.enabled=true"
})
@ActiveProfiles("local")
@Import({DemoDataSeeder.class, DeliveryTimelineService.class, DemoDataSeederTest.PasswordEncoderConfig.class})
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DemoDataSeederTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private DemoDataSeeder demoDataSeeder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DeliveryOrderRepository deliveryOrderRepository;

    @Autowired
    private DeliveryEventRepository deliveryEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void seedsIdempotentDemoUsersOrdersAndTimeline() throws Exception {
        demoDataSeeder.run(null);
        demoDataSeeder.run(null);

        assertThat(userRepository.count()).isEqualTo(5);
        assertThat(userRepository.findAll())
                .extracting(User::getRole)
                .containsExactlyInAnyOrder(
                        Role.ADMIN,
                        Role.DISPATCHER,
                        Role.CUSTOMER,
                        Role.COURIER,
                        Role.COURIER);

        User customer = userRepository.findByEmail("customer@delivery.local").orElseThrow();
        assertThat(passwordEncoder.matches("DemoPass123!", customer.getPasswordHash())).isTrue();

        List<User> couriers = userRepository.findAll().stream()
                .filter(user -> user.getRole() == Role.COURIER)
                .toList();
        assertThat(couriers)
                .allSatisfy(courier -> {
                    assertThat(courier.getCourierAvailabilityStatus())
                            .isEqualTo(CourierAvailabilityStatus.AVAILABLE);
                    assertThat(courier.getCourierLatitude()).isNotNull();
                    assertThat(courier.getCourierLongitude()).isNotNull();
                });

        List<DeliveryOrder> orders = deliveryOrderRepository
                .findByCustomerIdOrderByCreatedAtDescIdDesc(customer.getId());
        assertThat(orders)
                .extracting(DeliveryOrder::getStatus)
                .containsExactlyInAnyOrder(OrderStatus.PENDING, OrderStatus.DELIVERED);

        DeliveryOrder completedOrder = orders.stream()
                .filter(order -> order.getStatus() == OrderStatus.DELIVERED)
                .findFirst()
                .orElseThrow();
        assertThat(deliveryEventRepository.findByDeliveryOrderIdOrderByCreatedAtAscIdAsc(completedOrder.getId()))
                .extracting(event -> event.getEventType())
                .containsExactly(
                        DeliveryEventType.ORDER_CREATED,
                        DeliveryEventType.COURIER_ASSIGNED,
                        DeliveryEventType.ORDER_PICKED_UP,
                        DeliveryEventType.ORDER_DELIVERED);

        assertThat(deliveryOrderRepository.count()).isEqualTo(2);
        assertThat(deliveryEventRepository.count()).isEqualTo(5);
    }

    @TestConfiguration
    static class PasswordEncoderConfig {

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }
}
