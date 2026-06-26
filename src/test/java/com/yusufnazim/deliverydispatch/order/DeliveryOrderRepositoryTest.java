package com.yusufnazim.deliverydispatch.order;

import static org.assertj.core.api.Assertions.assertThat;

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
class DeliveryOrderRepositoryTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private DeliveryOrderRepository deliveryOrderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void savesPendingOrderForCustomer() {
        User customer = userRepository.save(new User(
                "customer@example.com",
                "hashed-password",
                Role.CUSTOMER));
        DeliveryOrder order = new DeliveryOrder(
                customer,
                "Istiklal Cd. No:1, Beyoglu",
                new BigDecimal("41.036900"),
                new BigDecimal("28.985000"),
                "Bagdat Cd. No:10, Kadikoy",
                new BigDecimal("40.970000"),
                new BigDecimal("29.057000"));

        DeliveryOrder savedOrder = deliveryOrderRepository.saveAndFlush(order);
        Long orderId = savedOrder.getId();
        entityManager.clear();

        DeliveryOrder foundOrder = deliveryOrderRepository.findById(orderId).orElseThrow();

        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(foundOrder.getCustomer().getId()).isEqualTo(customer.getId());
        assertThat(foundOrder.getCourier()).isNull();
        assertThat(foundOrder.getPickupLatitude()).isEqualByComparingTo("41.036900");
        assertThat(foundOrder.getPickupLongitude()).isEqualByComparingTo("28.985000");
        assertThat(foundOrder.getDropoffLatitude()).isEqualByComparingTo("40.970000");
        assertThat(foundOrder.getDropoffLongitude()).isEqualByComparingTo("29.057000");
        assertThat(foundOrder.getVersion()).isNotNull();
        assertThat(foundOrder.getCreatedAt()).isNotNull();
        assertThat(foundOrder.getUpdatedAt()).isNotNull();
    }

    @Test
    void findsCustomerOrdersNewestFirst() {
        User customer = userRepository.save(new User(
                "orders@example.com",
                "hashed-password",
                Role.CUSTOMER));
        DeliveryOrder firstOrder = deliveryOrderRepository.save(orderFor(customer, "Pickup A"));
        DeliveryOrder secondOrder = deliveryOrderRepository.save(orderFor(customer, "Pickup B"));
        deliveryOrderRepository.flush();
        entityManager.clear();

        List<DeliveryOrder> orders = deliveryOrderRepository.findByCustomerIdOrderByCreatedAtDescIdDesc(customer.getId());

        assertThat(orders)
                .extracting(DeliveryOrder::getId)
                .containsExactly(secondOrder.getId(), firstOrder.getId());
    }

    private DeliveryOrder orderFor(User customer, String pickupAddress) {
        return new DeliveryOrder(
                customer,
                pickupAddress,
                new BigDecimal("41.036900"),
                new BigDecimal("28.985000"),
                "Dropoff",
                new BigDecimal("40.970000"),
                new BigDecimal("29.057000"));
    }
}
