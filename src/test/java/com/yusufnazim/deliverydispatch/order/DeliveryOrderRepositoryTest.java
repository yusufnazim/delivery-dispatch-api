package com.yusufnazim.deliverydispatch.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import com.yusufnazim.deliverydispatch.user.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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

    @Test
    void findsOperationalOrdersWithCustomerAndCourierLoaded() {
        User customer = userRepository.save(new User(
                "operations-customer@example.com",
                "hashed-password",
                Role.CUSTOMER));
        User courier = userRepository.save(new User(
                "operations-courier@example.com",
                "hashed-password",
                Role.COURIER));
        DeliveryOrder firstOrder = deliveryOrderRepository.save(orderFor(customer, "Pickup A"));
        DeliveryOrder secondOrder = orderFor(customer, "Pickup B");
        secondOrder.assignCourier(courier);
        deliveryOrderRepository.saveAndFlush(secondOrder);
        entityManager.clear();

        List<DeliveryOrder> orders = deliveryOrderRepository.findAllByOrderByCreatedAtDescIdDesc();

        assertThat(orders)
                .extracting(DeliveryOrder::getId)
                .containsExactly(secondOrder.getId(), firstOrder.getId());
        assertThat(orders)
                .allSatisfy(order -> assertThat(Hibernate.isInitialized(order.getCustomer())).isTrue());
        assertThat(Hibernate.isInitialized(orders.getFirst().getCourier())).isTrue();
    }

    @Test
    void detectsActiveDeliveryForCourier() {
        User customer = userRepository.save(new User(
                "active-delivery-customer@example.com",
                "hashed-password",
                Role.CUSTOMER));
        User courier = userRepository.save(new User(
                "active-delivery-courier@example.com",
                "hashed-password",
                Role.COURIER));
        DeliveryOrder order = orderFor(customer, "Active pickup");
        order.assignCourier(courier);
        deliveryOrderRepository.saveAndFlush(order);
        entityManager.clear();

        boolean hasActiveDelivery = deliveryOrderRepository.existsByCourierIdAndStatusIn(
                courier.getId(),
                List.of(OrderStatus.ASSIGNED, OrderStatus.PICKED_UP));

        assertThat(hasActiveDelivery).isTrue();
    }

    @Test
    void detectsOptimisticConflictForStaleAssignment() {
        User customer = userRepository.save(new User(
                "stale-assignment-customer@example.com",
                "hashed-password",
                Role.CUSTOMER));
        User firstCourier = userRepository.save(new User(
                "first-conflict-courier@example.com",
                "hashed-password",
                Role.COURIER));
        User secondCourier = userRepository.save(new User(
                "second-conflict-courier@example.com",
                "hashed-password",
                Role.COURIER));
        DeliveryOrder savedOrder = deliveryOrderRepository.saveAndFlush(orderFor(customer, "Stale pickup"));
        Long orderId = savedOrder.getId();
        Long firstCourierId = firstCourier.getId();
        Long secondCourierId = secondCourier.getId();
        entityManager.clear();

        DeliveryOrder staleOrder = deliveryOrderRepository.findById(orderId).orElseThrow();
        User staleCourier = userRepository.findById(secondCourierId).orElseThrow();
        int updatedRows = entityManager.getEntityManager()
                .createNativeQuery("""
                        update delivery_orders
                        set status = 'ASSIGNED', courier_id = ?, version = version + 1
                        where id = ?
                        """)
                .setParameter(1, firstCourierId)
                .setParameter(2, orderId)
                .executeUpdate();

        staleOrder.assignCourier(staleCourier);

        assertThat(updatedRows).isEqualTo(1);
        assertThatThrownBy(deliveryOrderRepository::flush)
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"ASSIGNED", "PICKED_UP"})
    void databaseRejectsSecondActiveOrderForCourier(OrderStatus existingStatus) {
        User customer = userRepository.save(new User("constraint-customer@example.com", "hash", Role.CUSTOMER));
        User courier = userRepository.save(new User("constraint-courier@example.com", "hash", Role.COURIER));
        DeliveryOrder first = orderFor(customer, "First pickup");
        first.assignCourier(courier);
        if (existingStatus == OrderStatus.PICKED_UP) {
            first.markPickedUp();
        }
        deliveryOrderRepository.saveAndFlush(first);
        DeliveryOrder second = orderFor(customer, "Second pickup");
        second.assignCourier(courier);

        assertThatThrownBy(() -> deliveryOrderRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_delivery_orders_active_courier");
    }

    @Test
    void databaseAllowsMultipleCompletedOrdersAndOneActiveOrderForCourier() {
        User customer = userRepository.save(new User("history-customer@example.com", "hash", Role.CUSTOMER));
        User courier = userRepository.save(new User("history-courier@example.com", "hash", Role.COURIER));
        for (int i = 0; i < 2; i++) {
            DeliveryOrder completed = orderFor(customer, "Completed pickup " + i);
            completed.assignCourier(courier);
            completed.markPickedUp();
            completed.markDelivered();
            deliveryOrderRepository.saveAndFlush(completed);
        }
        DeliveryOrder active = orderFor(customer, "Active pickup");
        active.assignCourier(courier);
        deliveryOrderRepository.saveAndFlush(active);
        entityManager.clear();

        assertThat(deliveryOrderRepository.findAll()).hasSize(3);
        assertThat(deliveryOrderRepository.findById(active.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.ASSIGNED);
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
