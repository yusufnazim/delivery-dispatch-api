package com.yusufnazim.deliverydispatch.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yusufnazim.deliverydispatch.courier.dto.OperationalCourierResponse;
import com.yusufnazim.deliverydispatch.order.DeliveryOrder;
import com.yusufnazim.deliverydispatch.order.DeliveryOrderRepository;
import com.yusufnazim.deliverydispatch.order.OrderStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DeliveryOrderRepository deliveryOrderRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void savesCourierProfileFields() {
        User courier = new User("courier@example.com", "hashed-password", Role.COURIER);
        courier.updateCourierProfile("Yusuf Courier", "+905551112233", CourierVehicleType.MOTORBIKE);

        User savedCourier = userRepository.saveAndFlush(courier);
        Long courierId = savedCourier.getId();
        entityManager.clear();

        User foundCourier = userRepository.findById(courierId).orElseThrow();

        assertThat(foundCourier.getCourierDisplayName()).isEqualTo("Yusuf Courier");
        assertThat(foundCourier.getCourierPhoneNumber()).isEqualTo("+905551112233");
        assertThat(foundCourier.getCourierVehicleType()).isEqualTo(CourierVehicleType.MOTORBIKE);
        assertThat(foundCourier.getCourierAvailabilityStatus()).isEqualTo(CourierAvailabilityStatus.UNAVAILABLE);
    }

    @Test
    void savesCourierAvailabilityStatusUpdates() {
        User courier = new User("available-courier@example.com", "hashed-password", Role.COURIER);
        User savedCourier = userRepository.saveAndFlush(courier);
        savedCourier.updateCourierAvailabilityStatus(CourierAvailabilityStatus.AVAILABLE);

        Long courierId = userRepository.saveAndFlush(savedCourier).getId();
        entityManager.clear();

        User foundCourier = userRepository.findById(courierId).orElseThrow();

        assertThat(foundCourier.getCourierAvailabilityStatus()).isEqualTo(CourierAvailabilityStatus.AVAILABLE);
    }

    @Test
    void savesCourierLocationFields() {
        User courier = new User("location-courier@example.com", "hashed-password", Role.COURIER);
        courier.updateCourierLocation(new BigDecimal("41.008200"), new BigDecimal("28.978400"));

        User savedCourier = userRepository.saveAndFlush(courier);
        Long courierId = savedCourier.getId();
        entityManager.clear();

        User foundCourier = userRepository.findById(courierId).orElseThrow();

        assertThat(foundCourier.getCourierLatitude()).isEqualByComparingTo("41.008200");
        assertThat(foundCourier.getCourierLongitude()).isEqualByComparingTo("28.978400");
    }

    @Test
    void findsOnlyEligibleCouriersForDispatch() {
        User eligibleCourier = new User("eligible-courier@example.com", "hashed-password", Role.COURIER);
        eligibleCourier.updateCourierAvailabilityStatus(CourierAvailabilityStatus.AVAILABLE);
        eligibleCourier.updateCourierLocation(new BigDecimal("41.008200"), new BigDecimal("28.978400"));

        User unavailableCourier = new User("unavailable-courier@example.com", "hashed-password", Role.COURIER);
        unavailableCourier.updateCourierAvailabilityStatus(CourierAvailabilityStatus.UNAVAILABLE);
        unavailableCourier.updateCourierLocation(new BigDecimal("41.010000"), new BigDecimal("28.980000"));

        User onDeliveryCourier = new User("on-delivery-courier@example.com", "hashed-password", Role.COURIER);
        onDeliveryCourier.updateCourierAvailabilityStatus(CourierAvailabilityStatus.ON_DELIVERY);
        onDeliveryCourier.updateCourierLocation(new BigDecimal("41.020000"), new BigDecimal("28.990000"));

        User locationMissingCourier = new User("location-missing-courier@example.com", "hashed-password", Role.COURIER);
        locationMissingCourier.updateCourierAvailabilityStatus(CourierAvailabilityStatus.AVAILABLE);

        User busyCourier = new User("busy-courier@example.com", "hashed-password", Role.COURIER);
        busyCourier.updateCourierAvailabilityStatus(CourierAvailabilityStatus.AVAILABLE);
        busyCourier.updateCourierLocation(new BigDecimal("41.030000"), new BigDecimal("28.970000"));

        User customer = new User("customer-without-courier-fields@example.com", "hashed-password", Role.CUSTOMER);

        userRepository.saveAllAndFlush(List.of(
                eligibleCourier,
                unavailableCourier,
                onDeliveryCourier,
                locationMissingCourier,
                busyCourier,
                customer));
        DeliveryOrder activeOrder = orderFor(customer);
        activeOrder.assignCourier(busyCourier);
        deliveryOrderRepository.saveAndFlush(activeOrder);
        entityManager.clear();

        List<User> eligibleCouriers =
                userRepository.findEligibleCouriersForDispatch(
                        Role.COURIER,
                        CourierAvailabilityStatus.AVAILABLE,
                        List.of(OrderStatus.ASSIGNED, OrderStatus.PICKED_UP));

        assertThat(eligibleCouriers)
                .extracting(User::getEmail)
                .containsExactly("eligible-courier@example.com");
    }

    @Test
    void courierPagesKeepIdOrderAndRoleFilteredTotalsAcrossExactBoundaries() {
        User firstCourier = new User("first-courier@example.com", "hashed-password", Role.COURIER);
        firstCourier.updateCourierAvailabilityStatus(CourierAvailabilityStatus.AVAILABLE);
        userRepository.saveAndFlush(firstCourier);
        saveNonCourierUsers();
        User secondCourier = new User("second-courier@example.com", "hashed-password", Role.COURIER);
        userRepository.saveAndFlush(secondCourier);
        entityManager.clear();

        Page<User> first = userRepository.findByRoleOrderByIdAsc(Role.COURIER, PageRequest.of(0, 1));
        Page<User> last = userRepository.findByRoleOrderByIdAsc(Role.COURIER, PageRequest.of(1, 1));
        Page<User> beyondLast = userRepository.findByRoleOrderByIdAsc(Role.COURIER, PageRequest.of(2, 1));

        assertThat(first.getContent()).extracting(User::getId).containsExactly(firstCourier.getId());
        assertThat(last.getContent()).extracting(User::getId).containsExactly(secondCourier.getId());
        assertThat(beyondLast.getContent()).isEmpty();
        for (Page<User> page : List.of(first, last, beyondLast)) {
            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getTotalPages()).isEqualTo(2);
            assertThat(page.getContent()).allSatisfy(user -> assertThat(user.getRole()).isEqualTo(Role.COURIER));
        }
    }

    @ParameterizedTest
    @EnumSource(CourierAvailabilityStatus.class)
    void courierAvailabilityPagesUseMatchingTotalsAndIdOrdering(CourierAvailabilityStatus availability) {
        User first = saveCourier("first", availability);
        saveNonCourierUsers();
        saveCourier("other", availability == CourierAvailabilityStatus.AVAILABLE
                ? CourierAvailabilityStatus.UNAVAILABLE : CourierAvailabilityStatus.AVAILABLE);
        User second = saveCourier("second", availability);
        User third = saveCourier("third", availability);
        entityManager.clear();

        Page<User> firstPage = userRepository.findByRoleAndCourierAvailabilityStatusOrderByIdAsc(
                Role.COURIER, availability, PageRequest.of(0, 2));
        Page<User> lastPage = userRepository.findByRoleAndCourierAvailabilityStatusOrderByIdAsc(
                Role.COURIER, availability, PageRequest.of(1, 2));
        Page<User> beyondLast = userRepository.findByRoleAndCourierAvailabilityStatusOrderByIdAsc(
                Role.COURIER, availability, PageRequest.of(2, 2));

        assertThat(firstPage.getContent()).extracting(User::getId).containsExactly(first.getId(), second.getId());
        assertThat(lastPage.getContent()).extracting(User::getId).containsExactly(third.getId());
        assertThat(beyondLast.getContent()).isEmpty();
        for (Page<User> page : List.of(firstPage, lastPage, beyondLast)) {
            assertThat(page.getTotalElements()).isEqualTo(3);
            assertThat(page.getTotalPages()).isEqualTo(2);
            assertThat(page.getContent()).allSatisfy(user -> {
                assertThat(user.getRole()).isEqualTo(Role.COURIER);
                assertThat(user.getCourierAvailabilityStatus()).isEqualTo(availability);
            });
        }
    }

    @ParameterizedTest
    @CsvSource({"false,1", "false,2", "true,1", "true,2"})
    void courierPagesLoadOnlyRequestedEntitiesAndMapWithoutExtraQueries(boolean filtered, int size) {
        User first = saveCourier("first", CourierAvailabilityStatus.AVAILABLE);
        first.updateCourierProfile("Ayse Courier", "+905551112233", CourierVehicleType.MOTORBIKE);
        first.updateCourierLocation(new BigDecimal("41.008200"), new BigDecimal("28.978400"));
        User second = saveCourier("second", CourierAvailabilityStatus.AVAILABLE);
        saveCourier("third", CourierAvailabilityStatus.AVAILABLE);
        saveCourier("unavailable", CourierAvailabilityStatus.UNAVAILABLE);
        saveNonCourierUsers();
        userRepository.flush();
        entityManager.refresh(first);
        entityManager.refresh(second);
        OperationalCourierResponse expectedFirst = OperationalCourierResponse.from(first);
        OperationalCourierResponse expectedSecond = OperationalCourierResponse.from(second);
        entityManager.clear();
        Statistics statistics = entityManager.getEntityManager().getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        PageRequest request = PageRequest.of(0, size);
        Page<User> page = filtered
                ? userRepository.findByRoleAndCourierAvailabilityStatusOrderByIdAsc(
                        Role.COURIER, CourierAvailabilityStatus.AVAILABLE, request)
                : userRepository.findByRoleOrderByIdAsc(Role.COURIER, request);

        assertThat(statistics.getEntityLoadCount()).isEqualTo(size);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        entityManager.clear();
        List<OperationalCourierResponse> responses = page.map(OperationalCourierResponse::from).getContent();
        assertThat(responses).containsExactlyElementsOf(size == 1
                ? List.of(expectedFirst) : List.of(expectedFirst, expectedSecond));
        assertThat(page.getTotalElements()).isEqualTo(filtered ? 3 : 4);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void courierPagesHandleEmptyDatabaseAndUnmatchedAvailability(int pageNumber) {
        PageRequest request = PageRequest.of(pageNumber, 20);
        Page<User> emptyDatabase = userRepository.findByRoleOrderByIdAsc(Role.COURIER, request);
        Page<User> emptyFilteredDatabase = userRepository.findByRoleAndCourierAvailabilityStatusOrderByIdAsc(
                Role.COURIER, CourierAvailabilityStatus.ON_DELIVERY, request);
        saveNonCourierUsers();
        Page<User> noCouriers = userRepository.findByRoleOrderByIdAsc(Role.COURIER, request);
        saveCourier("available", CourierAvailabilityStatus.AVAILABLE);
        Page<User> noMatches = userRepository.findByRoleAndCourierAvailabilityStatusOrderByIdAsc(
                Role.COURIER, CourierAvailabilityStatus.ON_DELIVERY, request);

        for (Page<User> page : List.of(emptyDatabase, emptyFilteredDatabase, noCouriers, noMatches)) {
            assertThat(page.getContent()).isEmpty();
            assertThat(page.getTotalElements()).isZero();
            assertThat(page.getTotalPages()).isZero();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void courierPagesAcceptMaximumSupportedOffsetWithCorrectTotals(boolean filtered) {
        saveCourier("available", CourierAvailabilityStatus.AVAILABLE);
        saveCourier("unavailable", CourierAvailabilityStatus.UNAVAILABLE);
        saveNonCourierUsers();
        entityManager.clear();
        PageRequest request = PageRequest.of(Integer.MAX_VALUE / 100, 100);

        Page<User> page = filtered
                ? userRepository.findByRoleAndCourierAvailabilityStatusOrderByIdAsc(
                        Role.COURIER, CourierAvailabilityStatus.AVAILABLE, request)
                : userRepository.findByRoleOrderByIdAsc(Role.COURIER, request);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getNumber()).isEqualTo(Integer.MAX_VALUE / 100);
        assertThat(page.getSize()).isEqualTo(100);
        assertThat(page.getTotalElements()).isEqualTo(filtered ? 1 : 2);
        assertThat(page.getTotalPages()).isEqualTo(1);
    }

    @Test
    void eligibleCourierLookupRemainsUnpagedBeyondDefaultOperationalPage() {
        List<Long> courierIds = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            User courier = saveCourier("eligible-" + i, CourierAvailabilityStatus.AVAILABLE);
            courier.updateCourierLocation(new BigDecimal("41.008200"), new BigDecimal("28.978400"));
            courierIds.add(courier.getId());
        }
        userRepository.flush();
        entityManager.clear();

        Page<User> firstPage = userRepository.findByRoleAndCourierAvailabilityStatusOrderByIdAsc(
                Role.COURIER, CourierAvailabilityStatus.AVAILABLE, PageRequest.of(0, 20));
        List<User> eligible = userRepository.findEligibleCouriersForDispatch(
                Role.COURIER, CourierAvailabilityStatus.AVAILABLE, List.of(OrderStatus.ASSIGNED, OrderStatus.PICKED_UP));

        assertThat(firstPage.getContent()).hasSize(20);
        assertThat(firstPage.getTotalElements()).isEqualTo(21);
        assertThat(eligible).extracting(User::getId).containsExactlyElementsOf(courierIds);
    }

    @Test
    void savesNonCourierUserWithoutCourierProfileFields() {
        User customer = new User("customer@example.com", "hashed-password", Role.CUSTOMER);

        User savedCustomer = userRepository.saveAndFlush(customer);
        Long customerId = savedCustomer.getId();
        entityManager.clear();

        User foundCustomer = userRepository.findById(customerId).orElseThrow();

        assertThat(foundCustomer.getCourierDisplayName()).isNull();
        assertThat(foundCustomer.getCourierPhoneNumber()).isNull();
        assertThat(foundCustomer.getCourierVehicleType()).isNull();
        assertThat(foundCustomer.getCourierAvailabilityStatus()).isNull();
        assertThat(foundCustomer.getCourierLatitude()).isNull();
        assertThat(foundCustomer.getCourierLongitude()).isNull();
    }

    @Test
    void staleCourierUpdateCannotOverwriteNewerAvailability() {
        User courier = new User("stale-courier@example.com", "hash", Role.COURIER);
        courier.updateCourierAvailabilityStatus(CourierAvailabilityStatus.AVAILABLE);
        Long courierId = userRepository.saveAndFlush(courier).getId();
        entityManager.clear();
        User staleCourier = userRepository.findById(courierId).orElseThrow();
        int updatedRows = entityManager.getEntityManager().createNativeQuery("""
                update users
                set courier_availability_status = 'ON_DELIVERY', version = version + 1
                where id = ?
                """).setParameter(1, courierId).executeUpdate();
        staleCourier.updateCourierLocation(new BigDecimal("41.037000"), new BigDecimal("28.986000"));

        assertThat(updatedRows).isEqualTo(1);
        assertThatThrownBy(userRepository::flush).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    private User saveCourier(String name, CourierAvailabilityStatus availability) {
        User courier = new User(name + "@example.com", "hash", Role.COURIER);
        courier.updateCourierAvailabilityStatus(availability);
        return userRepository.saveAndFlush(courier);
    }

    private void saveNonCourierUsers() {
        userRepository.saveAllAndFlush(List.of(
                new User("customer-list@example.com", "hash", Role.CUSTOMER),
                new User("dispatcher-list@example.com", "hash", Role.DISPATCHER),
                new User("admin-list@example.com", "hash", Role.ADMIN)));
    }

    private DeliveryOrder orderFor(User customer) {
        return new DeliveryOrder(
                customer,
                "Pickup",
                new BigDecimal("41.036900"),
                new BigDecimal("28.985000"),
                "Dropoff",
                new BigDecimal("40.970000"),
                new BigDecimal("29.057000"));
    }
}
