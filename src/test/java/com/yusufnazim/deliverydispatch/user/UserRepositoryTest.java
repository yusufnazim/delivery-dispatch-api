package com.yusufnazim.deliverydispatch.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
}
