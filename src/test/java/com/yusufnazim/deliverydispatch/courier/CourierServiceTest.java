package com.yusufnazim.deliverydispatch.courier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yusufnazim.deliverydispatch.courier.dto.CourierAvailabilityResponse;
import com.yusufnazim.deliverydispatch.courier.dto.CourierLocationResponse;
import com.yusufnazim.deliverydispatch.courier.exception.CourierNotFoundException;
import com.yusufnazim.deliverydispatch.courier.exception.InvalidCourierAvailabilityStatusException;
import com.yusufnazim.deliverydispatch.order.DeliveryOrder;
import com.yusufnazim.deliverydispatch.order.DeliveryOrderRepository;
import com.yusufnazim.deliverydispatch.order.OrderStatus;
import com.yusufnazim.deliverydispatch.order.dto.DeliveryOrderResponse;
import com.yusufnazim.deliverydispatch.order.exception.InvalidOrderStatusTransitionException;
import com.yusufnazim.deliverydispatch.order.exception.OrderNotFoundException;
import com.yusufnazim.deliverydispatch.timeline.DeliveryTimelineService;
import com.yusufnazim.deliverydispatch.user.CourierAvailabilityStatus;
import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import com.yusufnazim.deliverydispatch.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CourierServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DeliveryOrderRepository deliveryOrderRepository;

    @Mock
    private DeliveryTimelineService deliveryTimelineService;

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

    @Test
    void pickupOrderMarksAssignedOrderAsPickedUp() {
        DeliveryOrder order = assignedOrder(11L);
        when(deliveryOrderRepository.findByIdAndCourierId(11L, 7L)).thenReturn(Optional.of(order));

        DeliveryOrderResponse response = courierService.pickupOrder(7L, 11L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PICKED_UP);
        assertThat(response.id()).isEqualTo(11L);
        assertThat(response.status()).isEqualTo(OrderStatus.PICKED_UP);
        verify(deliveryTimelineService).recordOrderPickedUp(order);
    }

    @Test
    void pickupOrderRejectsMissingOrWrongCourierOrder() {
        when(deliveryOrderRepository.findByIdAndCourierId(11L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courierService.pickupOrder(7L, 11L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order not found: 11");
    }

    @Test
    void pickupOrderRejectsInvalidStatus() {
        DeliveryOrder order = assignedOrder(11L);
        order.markPickedUp();
        when(deliveryOrderRepository.findByIdAndCourierId(11L, 7L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> courierService.pickupOrder(7L, 11L))
                .isInstanceOf(InvalidOrderStatusTransitionException.class)
                .hasMessage("Order cannot transition from PICKED_UP to PICKED_UP");
    }

    @Test
    void deliverOrderMarksPickedUpOrderAsDeliveredAndMakesCourierAvailable() {
        DeliveryOrder order = assignedOrder(11L);
        order.markPickedUp();
        when(deliveryOrderRepository.findByIdAndCourierId(11L, 7L)).thenReturn(Optional.of(order));

        DeliveryOrderResponse response = courierService.deliverOrder(7L, 11L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getCourier().getCourierAvailabilityStatus()).isEqualTo(CourierAvailabilityStatus.AVAILABLE);
        assertThat(response.id()).isEqualTo(11L);
        assertThat(response.status()).isEqualTo(OrderStatus.DELIVERED);
        verify(deliveryTimelineService).recordOrderDelivered(order);
    }

    @Test
    void deliverOrderRejectsMissingOrWrongCourierOrder() {
        when(deliveryOrderRepository.findByIdAndCourierId(11L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courierService.deliverOrder(7L, 11L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order not found: 11");
    }

    @Test
    void deliverOrderRejectsInvalidStatus() {
        DeliveryOrder order = assignedOrder(11L);
        when(deliveryOrderRepository.findByIdAndCourierId(11L, 7L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> courierService.deliverOrder(7L, 11L))
                .isInstanceOf(InvalidOrderStatusTransitionException.class)
                .hasMessage("Order cannot transition from ASSIGNED to DELIVERED");
        assertThat(order.getCourier().getCourierAvailabilityStatus()).isEqualTo(CourierAvailabilityStatus.ON_DELIVERY);
    }

    private DeliveryOrder assignedOrder(Long orderId) {
        User customer = new User("customer@example.com", "hashed-password", Role.CUSTOMER);
        User courier = new User("courier@example.com", "hashed-password", Role.COURIER);
        DeliveryOrder order = new DeliveryOrder(
                customer,
                "Istiklal Cd. No:1, Beyoglu",
                new BigDecimal("41.036900"),
                new BigDecimal("28.985000"),
                "Bagdat Cd. No:10, Kadikoy",
                new BigDecimal("40.970000"),
                new BigDecimal("29.057000"));
        ReflectionTestUtils.setField(order, "id", orderId);
        ReflectionTestUtils.setField(order, "createdAt", Instant.parse("2026-06-30T10:00:00Z"));
        ReflectionTestUtils.setField(order, "updatedAt", Instant.parse("2026-06-30T10:05:00Z"));
        order.assignCourier(courier);
        courier.updateCourierAvailabilityStatus(CourierAvailabilityStatus.ON_DELIVERY);
        return order;
    }
}
