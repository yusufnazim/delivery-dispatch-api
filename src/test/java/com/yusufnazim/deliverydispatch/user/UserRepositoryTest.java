package com.yusufnazim.deliverydispatch.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.yusufnazim.deliverydispatch.order.DeliveryOrder;
import com.yusufnazim.deliverydispatch.order.DeliveryOrderRepository;
import com.yusufnazim.deliverydispatch.order.OrderStatus;
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
    void findsAllCouriersOrderedById() {
        User firstCourier = new User("first-courier@example.com", "hashed-password", Role.COURIER);
        User customer = new User("listing-customer@example.com", "hashed-password", Role.CUSTOMER);
        User secondCourier = new User("second-courier@example.com", "hashed-password", Role.COURIER);
        userRepository.saveAllAndFlush(List.of(firstCourier, customer, secondCourier));
        entityManager.clear();

        List<User> couriers = userRepository.findByRoleOrderByIdAsc(Role.COURIER);

        assertThat(couriers)
                .extracting(User::getEmail)
                .containsExactly("first-courier@example.com", "second-courier@example.com");
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
