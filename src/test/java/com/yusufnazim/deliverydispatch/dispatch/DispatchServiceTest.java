package com.yusufnazim.deliverydispatch.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yusufnazim.deliverydispatch.user.CourierAvailabilityStatus;
import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import com.yusufnazim.deliverydispatch.user.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DispatchServiceTest {

    @Mock
    private UserRepository userRepository;

    private DispatchService dispatchService;

    @BeforeEach
    void setUp() {
        dispatchService = new DispatchService(userRepository, new HaversineDistanceCalculator());
    }

    @Test
    void findEligibleCouriersLooksUpAvailableCouriersWithKnownLocations() {
        User courier = new User("courier@example.com", "hashed-password", Role.COURIER);
        when(userRepository.findEligibleCouriersForDispatch(Role.COURIER, CourierAvailabilityStatus.AVAILABLE))
                .thenReturn(List.of(courier));

        List<User> eligibleCouriers = dispatchService.findEligibleCouriers();

        assertThat(eligibleCouriers).containsExactly(courier);
    }

    @Test
    void findNearestEligibleCourierSelectsClosestCourierToPickupLocation() {
        User farCourier = courierAt("far-courier@example.com", "40.991000", "29.024400");
        User nearestCourier = courierAt("nearest-courier@example.com", "41.037200", "28.985300");
        User middleCourier = courierAt("middle-courier@example.com", "41.027000", "28.974000");
        when(userRepository.findEligibleCouriersForDispatch(Role.COURIER, CourierAvailabilityStatus.AVAILABLE))
                .thenReturn(List.of(farCourier, nearestCourier, middleCourier));

        Optional<User> nearestCourierResult = dispatchService.findNearestEligibleCourier(
                new BigDecimal("41.036900"),
                new BigDecimal("28.985000"));

        assertThat(nearestCourierResult).contains(nearestCourier);
    }

    @Test
    void findNearestEligibleCourierReturnsEmptyWhenNoCourierIsEligible() {
        when(userRepository.findEligibleCouriersForDispatch(Role.COURIER, CourierAvailabilityStatus.AVAILABLE))
                .thenReturn(List.of());

        Optional<User> nearestCourier = dispatchService.findNearestEligibleCourier(
                new BigDecimal("41.036900"),
                new BigDecimal("28.985000"));

        assertThat(nearestCourier).isEmpty();
    }

    private static User courierAt(String email, String latitude, String longitude) {
        User courier = new User(email, "hashed-password", Role.COURIER);
        courier.updateCourierAvailabilityStatus(CourierAvailabilityStatus.AVAILABLE);
        courier.updateCourierLocation(new BigDecimal(latitude), new BigDecimal(longitude));
        return courier;
    }
}
