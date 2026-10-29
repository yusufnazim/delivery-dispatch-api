package com.yusufnazim.deliverydispatch.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yusufnazim.deliverydispatch.order.dto.OperationalOrderResponse;
import com.yusufnazim.deliverydispatch.user.CourierVehicleType;
import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import com.yusufnazim.deliverydispatch.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.session.events.log=false"
})
@Testcontainers
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
    void pagesOnlyOwnedOrdersByTimestampThenIdWithoutLoadingTheFullHistory() {
        User customer = userRepository.save(new User("orders@example.com", "hash", Role.CUSTOMER));
        User otherCustomer = userRepository.save(new User("other-orders@example.com", "hash", Role.CUSTOMER));
        DeliveryOrder newest = deliveryOrderRepository.save(orderFor(customer, "Newest"));
        DeliveryOrder older = deliveryOrderRepository.save(orderFor(customer, "Older"));
        DeliveryOrder tied = deliveryOrderRepository.save(orderFor(customer, "Same time, higher id"));
        deliveryOrderRepository.save(orderFor(otherCustomer, "Other customer's order"));
        deliveryOrderRepository.flush();
        setCreatedAt(newest, "2026-10-28T12:00:00Z");
        setCreatedAt(older, "2026-10-28T11:00:00Z");
        setCreatedAt(tied, "2026-10-28T11:00:00Z");
        entityManager.clear();
        Statistics statistics = entityManager.getEntityManager().getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        Page<DeliveryOrder> firstPage = deliveryOrderRepository
                .findByCustomerIdOrderByCreatedAtDescIdDesc(customer.getId(), PageRequest.of(0, 2));

        assertThat(firstPage.getContent()).extracting(DeliveryOrder::getId)
                .containsExactly(newest.getId(), tied.getId());
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(statistics.getEntityLoadCount()).isEqualTo(2);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        assertThat(firstPage.getContent()).allSatisfy(order ->
                assertThat(Hibernate.isInitialized(order.getCustomer())).isFalse());

        Page<DeliveryOrder> lastPage = deliveryOrderRepository
                .findByCustomerIdOrderByCreatedAtDescIdDesc(customer.getId(), PageRequest.of(1, 2));

        assertThat(lastPage.getContent()).extracting(DeliveryOrder::getId).containsExactly(older.getId());
        assertThat(lastPage.getTotalElements()).isEqualTo(3);
        assertThat(lastPage.getTotalPages()).isEqualTo(2);
    }

    @Test
    void emptyCustomerPagesDoNotIncludeAnotherCustomersItemsOrTotals() {
        User emptyCustomer = userRepository.save(new User("empty@example.com", "hash", Role.CUSTOMER));
        User otherCustomer = userRepository.save(new User("other@example.com", "hash", Role.CUSTOMER));
        deliveryOrderRepository.saveAndFlush(orderFor(otherCustomer, "Other customer's order"));
        entityManager.clear();

        for (int pageNumber : List.of(0, 1)) {
            Page<DeliveryOrder> page = deliveryOrderRepository
                    .findByCustomerIdOrderByCreatedAtDescIdDesc(emptyCustomer.getId(), PageRequest.of(pageNumber, 20));

            assertThat(page.getContent()).isEmpty();
            assertThat(page.getNumber()).isEqualTo(pageNumber);
            assertThat(page.getSize()).isEqualTo(20);
            assertThat(page.getTotalElements()).isZero();
            assertThat(page.getTotalPages()).isZero();
        }
    }

    @Test
    void exactPageBoundariesAndBeyondLastPagesRetainOwnedTotals() {
        User customer = userRepository.save(new User("boundaries@example.com", "hash", Role.CUSTOMER));
        User otherCustomer = userRepository.save(new User("other-boundaries@example.com", "hash", Role.CUSTOMER));
        for (int i = 0; i < 4; i++) {
            deliveryOrderRepository.save(orderFor(customer, "Pickup " + i));
        }
        deliveryOrderRepository.saveAndFlush(orderFor(otherCustomer, "Not in the total"));
        entityManager.clear();

        Page<DeliveryOrder> lastPage = deliveryOrderRepository
                .findByCustomerIdOrderByCreatedAtDescIdDesc(customer.getId(), PageRequest.of(1, 2));

        assertThat(lastPage.getContent()).hasSize(2);
        assertThat(lastPage.getTotalElements()).isEqualTo(4);
        assertThat(lastPage.getTotalPages()).isEqualTo(2);
        for (int pageNumber : List.of(2, 3)) {
            Page<DeliveryOrder> page = deliveryOrderRepository
                    .findByCustomerIdOrderByCreatedAtDescIdDesc(customer.getId(), PageRequest.of(pageNumber, 2));

            assertThat(page.getContent()).isEmpty();
            assertThat(page.getNumber()).isEqualTo(pageNumber);
            assertThat(page.getTotalElements()).isEqualTo(4);
            assertThat(page.getTotalPages()).isEqualTo(2);
        }

        Page<DeliveryOrder> maximumPage = deliveryOrderRepository
                .findByCustomerIdOrderByCreatedAtDescIdDesc(customer.getId(), PageRequest.of(Integer.MAX_VALUE / 100, 100));
        assertThat(maximumPage.getContent()).isEmpty();
        assertThat(maximumPage.getTotalElements()).isEqualTo(4);
        assertThat(maximumPage.getTotalPages()).isEqualTo(1);
    }

    @Test
    void customerOrderExistenceIsScopedToTheCustomer() {
        User customer = userRepository.save(new User("exists@example.com", "hash", Role.CUSTOMER));
        User otherCustomer = userRepository.save(new User("does-not-exist@example.com", "hash", Role.CUSTOMER));
        assertThat(deliveryOrderRepository.existsByCustomerId(customer.getId())).isFalse();
        deliveryOrderRepository.saveAndFlush(orderFor(customer, "Existing order"));
        entityManager.clear();

        assertThat(deliveryOrderRepository.existsByCustomerId(customer.getId())).isTrue();
        assertThat(deliveryOrderRepository.existsByCustomerId(otherCustomer.getId())).isFalse();
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

        List<DeliveryOrder> orders = deliveryOrderRepository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, 20))
                .getContent();

        assertThat(orders)
                .extracting(DeliveryOrder::getId)
                .containsExactly(secondOrder.getId(), firstOrder.getId());
        assertThat(orders)
                .allSatisfy(order -> assertThat(Hibernate.isInitialized(order.getCustomer())).isTrue());
        assertThat(Hibernate.isInitialized(orders.getFirst().getCourier())).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"false,1", "false,3", "true,1", "true,3"})
    void operationalPagesLoadOnlyRequestedOrdersAndMapDetailsWithoutAdditionalQueries(boolean filtered, int size) {
        List<DeliveryOrder> seeded = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            User customer = userRepository.save(new User("operational-" + i + "@example.com", "hash", Role.CUSTOMER));
            seeded.add(saveOrderWithStatus(customer, OrderStatus.ASSIGNED, "operational-" + i));
        }
        User pendingCustomer = userRepository.save(new User("pending-customer@example.com", "hash", Role.CUSTOMER));
        DeliveryOrder pending = deliveryOrderRepository.saveAndFlush(orderFor(pendingCustomer, "Pending"));
        for (int i = 0; i < seeded.size(); i++) {
            setCreatedAt(seeded.get(i), "2026-10-28T10:00:0" + i + "Z");
        }
        setCreatedAt(pending, "2026-10-28T11:00:00Z");
        if (!filtered) {
            seeded.add(pending);
        }
        entityManager.clear();
        Statistics statistics = entityManager.getEntityManager().getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        PageRequest request = PageRequest.of(0, size);
        Page<DeliveryOrder> page = filtered
                ? deliveryOrderRepository.findByStatusOrderByCreatedAtDescIdDesc(OrderStatus.ASSIGNED, request)
                : deliveryOrderRepository.findAllByOrderByCreatedAtDescIdDesc(request);
        entityManager.clear();
        List<OperationalOrderResponse> responses = page.map(OperationalOrderResponse::from).getContent();

        List<DeliveryOrder> expected = seeded.reversed().subList(0, size);
        assertThat(responses).extracting(OperationalOrderResponse::id)
                .containsExactlyElementsOf(expected.stream().map(DeliveryOrder::getId).toList());
        assertThat(page.getTotalElements()).isEqualTo(filtered ? 5 : 6);
        for (int i = 0; i < responses.size(); i++) {
            OperationalOrderResponse response = responses.get(i);
            DeliveryOrder order = expected.get(i);
            assertThat(response.customerId()).isEqualTo(order.getCustomer().getId());
            assertThat(response.customerEmail()).isEqualTo(order.getCustomer().getEmail());
            assertThat(response.status()).isEqualTo(order.getStatus());
            if (order.getCourier() == null) {
                assertThat(response.courierId()).isNull();
                assertThat(response.courierEmail()).isNull();
                assertThat(response.courierDisplayName()).isNull();
            } else {
                assertThat(response.courierId()).isEqualTo(order.getCourier().getId());
                assertThat(response.courierEmail()).isEqualTo(order.getCourier().getEmail());
                assertThat(response.courierDisplayName()).isEqualTo(order.getCourier().getCourierDisplayName());
            }
        }
        assertThat(statistics.getEntityStatistics(DeliveryOrder.class.getName()).getLoadCount()).isEqualTo(size);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);

        PageRequest beyondLast = PageRequest.of(6, size);
        Page<DeliveryOrder> emptyPage = filtered
                ? deliveryOrderRepository.findByStatusOrderByCreatedAtDescIdDesc(OrderStatus.ASSIGNED, beyondLast)
                : deliveryOrderRepository.findAllByOrderByCreatedAtDescIdDesc(beyondLast);
        assertThat(emptyPage.getContent()).isEmpty();
        assertThat(emptyPage.getTotalElements()).isEqualTo(filtered ? 5 : 6);
        assertThat(emptyPage.getTotalPages()).isEqualTo(page.getTotalPages());
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void operationalStatusFilterUsesMatchingTotalsAndStableOrderingAcrossPages(OrderStatus status) {
        User customer = userRepository.save(new User("filter-customer@example.com", "hash", Role.CUSTOMER));
        User otherCustomer = userRepository.save(new User("filter-other@example.com", "hash", Role.CUSTOMER));
        DeliveryOrder newest = saveOrderWithStatus(customer, status, "first");
        DeliveryOrder older = saveOrderWithStatus(otherCustomer, status, "second");
        DeliveryOrder tied = saveOrderWithStatus(otherCustomer, status, "third");
        saveOrderWithStatus(customer, status == OrderStatus.PENDING ? OrderStatus.CANCELLED : OrderStatus.PENDING, "other");
        deliveryOrderRepository.flush();
        setCreatedAt(newest, "2026-10-28T12:00:00Z");
        setCreatedAt(older, "2026-10-28T11:00:00Z");
        setCreatedAt(tied, "2026-10-28T11:00:00Z");
        entityManager.clear();

        Page<DeliveryOrder> firstPage = deliveryOrderRepository
                .findByStatusOrderByCreatedAtDescIdDesc(status, PageRequest.of(0, 2));
        Page<DeliveryOrder> lastPage = deliveryOrderRepository
                .findByStatusOrderByCreatedAtDescIdDesc(status, PageRequest.of(1, 2));
        Page<DeliveryOrder> beyondLast = deliveryOrderRepository
                .findByStatusOrderByCreatedAtDescIdDesc(status, PageRequest.of(2, 2));

        assertThat(firstPage.getContent()).extracting(DeliveryOrder::getId).containsExactly(newest.getId(), tied.getId());
        assertThat(lastPage.getContent()).extracting(DeliveryOrder::getId).containsExactly(older.getId());
        assertThat(beyondLast.getContent()).isEmpty();
        for (Page<DeliveryOrder> page : List.of(firstPage, lastPage, beyondLast)) {
            assertThat(page.getTotalElements()).isEqualTo(3);
            assertThat(page.getTotalPages()).isEqualTo(2);
            assertThat(page.getContent()).allSatisfy(order -> assertThat(order.getStatus()).isEqualTo(status));
        }
    }

    @Test
    void operationalPagesHandleEmptyDatabaseAndUnmatchedStatus() {
        for (int pageNumber : List.of(0, 1)) {
            Page<DeliveryOrder> page = deliveryOrderRepository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(pageNumber, 20));
            assertThat(page.getContent()).isEmpty();
            assertThat(page.getTotalElements()).isZero();
            assertThat(page.getTotalPages()).isZero();
        }
        User customer = userRepository.save(new User("no-match@example.com", "hash", Role.CUSTOMER));
        deliveryOrderRepository.saveAndFlush(orderFor(customer, "Pending only"));
        entityManager.clear();

        for (int pageNumber : List.of(0, 1)) {
            Page<DeliveryOrder> page = deliveryOrderRepository
                    .findByStatusOrderByCreatedAtDescIdDesc(OrderStatus.DELIVERED, PageRequest.of(pageNumber, 20));
            assertThat(page.getContent()).isEmpty();
            assertThat(page.getTotalElements()).isZero();
            assertThat(page.getTotalPages()).isZero();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void operationalPagesAcceptMaximumSupportedOffsetWithCorrectTotals(boolean filtered) {
        User customer = userRepository.save(new User("maximum-page@example.com", "hash", Role.CUSTOMER));
        saveOrderWithStatus(customer, OrderStatus.ASSIGNED, "maximum-page");
        deliveryOrderRepository.saveAndFlush(orderFor(customer, "Pending"));
        entityManager.clear();

        PageRequest request = PageRequest.of(Integer.MAX_VALUE / 100, 100);
        Page<DeliveryOrder> page = filtered
                ? deliveryOrderRepository.findByStatusOrderByCreatedAtDescIdDesc(OrderStatus.ASSIGNED, request)
                : deliveryOrderRepository.findAllByOrderByCreatedAtDescIdDesc(request);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getNumber()).isEqualTo(Integer.MAX_VALUE / 100);
        assertThat(page.getSize()).isEqualTo(100);
        assertThat(page.getTotalElements()).isEqualTo(filtered ? 1 : 2);
        assertThat(page.getTotalPages()).isEqualTo(1);
    }

    @Test
    void unfilteredOperationalPagesPreserveExactBoundariesAndIdTieBreaking() {
        User customer = userRepository.save(new User("operational-boundaries@example.com", "hash", Role.CUSTOMER));
        DeliveryOrder first = deliveryOrderRepository.save(orderFor(customer, "First"));
        DeliveryOrder second = saveOrderWithStatus(customer, OrderStatus.CANCELLED, "Second");
        deliveryOrderRepository.flush();
        setCreatedAt(first, "2026-10-28T11:00:00Z");
        setCreatedAt(second, "2026-10-28T11:00:00Z");
        entityManager.clear();

        Page<DeliveryOrder> firstPage = deliveryOrderRepository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, 1));
        Page<DeliveryOrder> lastPage = deliveryOrderRepository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(1, 1));
        Page<DeliveryOrder> beyondLast = deliveryOrderRepository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(2, 1));

        assertThat(firstPage.getContent()).extracting(DeliveryOrder::getId).containsExactly(second.getId());
        assertThat(lastPage.getContent()).extracting(DeliveryOrder::getId).containsExactly(first.getId());
        assertThat(beyondLast.getContent()).isEmpty();
        for (Page<DeliveryOrder> page : List.of(firstPage, lastPage, beyondLast)) {
            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getTotalPages()).isEqualTo(2);
        }
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

    private DeliveryOrder saveOrderWithStatus(User customer, OrderStatus status, String pickupAddress) {
        DeliveryOrder order = orderFor(customer, pickupAddress);
        if (status == OrderStatus.CANCELLED) {
            order.cancel();
        } else if (status != OrderStatus.PENDING) {
            User courier = new User("courier-" + pickupAddress + "@example.com", "hash", Role.COURIER);
            courier.updateCourierProfile("Courier " + pickupAddress, "+905550000001", CourierVehicleType.BICYCLE);
            userRepository.save(courier);
            order.assignCourier(courier);
            if (status == OrderStatus.PICKED_UP || status == OrderStatus.DELIVERED) {
                order.markPickedUp();
            }
            if (status == OrderStatus.DELIVERED) {
                order.markDelivered();
            }
        }
        return deliveryOrderRepository.save(order);
    }

    private void setCreatedAt(DeliveryOrder order, String timestamp) {
        entityManager.getEntityManager().createNativeQuery("update delivery_orders set created_at = :time where id = :id")
                .setParameter("time", Instant.parse(timestamp))
                .setParameter("id", order.getId())
                .executeUpdate();
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
