package com.yusufnazim.deliverydispatch.courier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.yusufnazim.deliverydispatch.courier.dto.CourierAvailabilityResponse;
import com.yusufnazim.deliverydispatch.courier.dto.CourierLocationResponse;
import com.yusufnazim.deliverydispatch.courier.exception.CourierNotFoundException;
import com.yusufnazim.deliverydispatch.courier.exception.InvalidCourierAvailabilityStatusException;
import com.yusufnazim.deliverydispatch.user.CourierAvailabilityStatus;
import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import com.yusufnazim.deliverydispatch.user.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourierServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CourierService courierService;

    @Test
    void updateAvailabilityUpdatesOwnedCourierStatus() {
        User courier = new User("courier@example.com", "hashed-password", Role.COURIER);
        when(userRepository.findByIdAndRole(7L, Role.COURIER)).thenReturn(Optional.of(courier));

        CourierAvailabilityResponse response =
                courierService.updateAvailability(7L, CourierAvailabilityStatus.AVAILABLE);

        assertThat(courier.getCourierAvailabilityStatus()).isEqualTo(CourierAvailabilityStatus.AVAILABLE);
        assertThat(response.status()).isEqualTo(CourierAvailabilityStatus.AVAILABLE);
    }

    @Test
    void updateAvailabilityRejectsMissingOrNonCourierUser() {
        when(userRepository.findByIdAndRole(7L, Role.COURIER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courierService.updateAvailability(7L, CourierAvailabilityStatus.AVAILABLE))
                .isInstanceOf(CourierNotFoundException.class)
                .hasMessage("Courier not found: 7");
    }

    @Test
    void updateAvailabilityRejectsSystemManagedStatus() {
        assertThatThrownBy(() -> courierService.updateAvailability(7L, CourierAvailabilityStatus.ON_DELIVERY))
                .isInstanceOf(InvalidCourierAvailabilityStatusException.class)
                .hasMessage("Courier availability status cannot be self-managed: ON_DELIVERY");
    }

    @Test
    void updateLocationUpdatesOwnedCourierCoordinates() {
        User courier = new User("courier@example.com", "hashed-password", Role.COURIER);
        when(userRepository.findByIdAndRole(7L, Role.COURIER)).thenReturn(Optional.of(courier));

        CourierLocationResponse response = courierService.updateLocation(
                7L,
                new BigDecimal("41.008200"),
                new BigDecimal("28.978400"));

        assertThat(courier.getCourierLatitude()).isEqualByComparingTo("41.008200");
        assertThat(courier.getCourierLongitude()).isEqualByComparingTo("28.978400");
        assertThat(response.latitude()).isEqualByComparingTo("41.008200");
        assertThat(response.longitude()).isEqualByComparingTo("28.978400");
    }

    @Test
    void updateLocationRejectsMissingOrNonCourierUser() {
        when(userRepository.findByIdAndRole(7L, Role.COURIER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courierService.updateLocation(
                        7L,
                        new BigDecimal("41.008200"),
                        new BigDecimal("28.978400")))
                .isInstanceOf(CourierNotFoundException.class)
                .hasMessage("Courier not found: 7");
    }
}
