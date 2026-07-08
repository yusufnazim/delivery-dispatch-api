package com.yusufnazim.deliverydispatch.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yusufnazim.deliverydispatch.courier.exception.CourierNotFoundException;
import com.yusufnazim.deliverydispatch.dispatch.exception.CourierAlreadyHasActiveDeliveryException;
import com.yusufnazim.deliverydispatch.dispatch.exception.CourierNotEligibleForDispatchException;
import com.yusufnazim.deliverydispatch.dispatch.exception.NoEligibleCourierException;
import com.yusufnazim.deliverydispatch.order.DeliveryOrder;
import com.yusufnazim.deliverydispatch.order.DeliveryOrderRepository;
import com.yusufnazim.deliverydispatch.order.OrderStatus;
import com.yusufnazim.deliverydispatch.order.exception.OrderAssignmentConflictException;
import com.yusufnazim.deliverydispatch.order.exception.OrderAssignmentNotAllowedException;
import com.yusufnazim.deliverydispatch.order.exception.OrderNotFoundException;
import com.yusufnazim.deliverydispatch.timeline.DeliveryTimelineService;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class DispatchServiceTest {

    @Mock
    private DeliveryOrderRepository deliveryOrderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DeliveryTimelineService deliveryTimelineService;

    private DispatchService dispatchService;

    @BeforeEach
    void setUp() {
        dispatchService = new DispatchService(
                deliveryOrderRepository,
                userRepository,
                new HaversineDistanceCalculator(),
                deliveryTimelineService);
    }

    @Test
    void findEligibleCouriersLooksUpAvailableCouriersWithKnownLocations() {
        User courier = new User("courier@example.com", "hashed-password", Role.COURIER);
        when(userRepository.findEligibleCouriersForDispatch(
                Role.COURIER,
                CourierAvailabilityStatus.AVAILABLE,
                List.of(OrderStatus.ASSIGNED, OrderStatus.PICKED_UP)))
                .thenReturn(List.of(courier));

        List<User> eligibleCouriers = dispatchService.findEligibleCouriers();

        assertThat(eligibleCouriers).containsExactly(courier);
    }

    @Test
    void findNearestEligibleCourierSelectsClosestCourierToPickupLocation() {
        User farCourier = courierAt("far-courier@example.com", "40.991000", "29.024400");
        User nearestCourier = courierAt("nearest-courier@example.com", "41.037200", "28.985300");
        User middleCourier = courierAt("middle-courier@example.com", "41.027000", "28.974000");
        when(userRepository.findEligibleCouriersForDispatch(
                Role.COURIER,
                CourierAvailabilityStatus.AVAILABLE,
                List.of(OrderStatus.ASSIGNED, OrderStatus.PICKED_UP)))
                .thenReturn(List.of(farCourier, nearestCourier, middleCourier));

        Optional<User> nearestCourierResult = dispatchService.findNearestEligibleCourier(
                new BigDecimal("41.036900"),
                new BigDecimal("28.985000"));

        assertThat(nearestCourierResult).contains(nearestCourier);
    }

    @Test
    void findNearestEligibleCourierReturnsEmptyWhenNoCourierIsEligible() {
        when(userRepository.findEligibleCouriersForDispatch(
                Role.COURIER,
                CourierAvailabilityStatus.AVAILABLE,
                List.of(OrderStatus.ASSIGNED, OrderStatus.PICKED_UP)))
                .thenReturn(List.of());

        Optional<User> nearestCourier = dispatchService.findNearestEligibleCourier(
                new BigDecimal("41.036900"),
                new BigDecimal("28.985000"));

        assertThat(nearestCourier).isEmpty();
    }

    @Test
    void assignNearestEligibleCourierAssignsNearestCourierToPendingOrder() {
        DeliveryOrder order = order();
        User farCourier = courierAt("far-courier@example.com", "40.991000", "29.024400");
        User nearestCourier = courierAt("nearest-courier@example.com", "41.037200", "28.985300");
        when(deliveryOrderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(userRepository.findEligibleCouriersForDispatch(
                Role.COURIER,
                CourierAvailabilityStatus.AVAILABLE,
                List.of(OrderStatus.ASSIGNED, OrderStatus.PICKED_UP)))
                .thenReturn(List.of(farCourier, nearestCourier));

        DeliveryOrder assignedOrder = dispatchService.assignNearestEligibleCourier(100L);

        assertThat(assignedOrder).isSameAs(order);
        assertThat(assignedOrder.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(assignedOrder.getCourier()).isEqualTo(nearestCourier);
        assertThat(nearestCourier.getCourierAvailabilityStatus()).isEqualTo(CourierAvailabilityStatus.ON_DELIVERY);
        verify(deliveryTimelineService).recordCourierAssigned(order);
    }

    @Test
    void assignNearestEligibleCourierRejectsMissingOrder() {
        when(deliveryOrderRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dispatchService.assignNearestEligibleCourier(404L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order not found: 404");
    }

    @Test
    void assignNearestEligibleCourierRejectsNonPendingOrder() {
        DeliveryOrder order = order();
        order.cancel();
        when(deliveryOrderRepository.findById(100L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> dispatchService.assignNearestEligibleCourier(100L))
                .isInstanceOf(OrderAssignmentNotAllowedException.class)
                .hasMessage("Order cannot be assigned from status: CANCELLED");
    }

    @Test
    void assignNearestEligibleCourierRejectsAlreadyAssignedOrder() {
        DeliveryOrder order = assignedOrder();
        when(deliveryOrderRepository.findById(100L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> dispatchService.assignNearestEligibleCourier(100L))
                .isInstanceOf(OrderAssignmentNotAllowedException.class)
                .hasMessage("Order cannot be assigned from status: ASSIGNED");
        verifyNoInteractions(userRepository);
    }

    @Test
    void assignNearestEligibleCourierRejectsWhenNoCourierIsEligible() {
        DeliveryOrder order = order();
        when(deliveryOrderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(userRepository.findEligibleCouriersForDispatch(
                Role.COURIER,
                CourierAvailabilityStatus.AVAILABLE,
                List.of(OrderStatus.ASSIGNED, OrderStatus.PICKED_UP)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> dispatchService.assignNearestEligibleCourier(100L))
                .isInstanceOf(NoEligibleCourierException.class)
                .hasMessage("No eligible courier found for order: 100");
    }

    @Test
    void assignNearestEligibleCourierConvertsOptimisticLockFailureToAssignmentConflict() {
        DeliveryOrder order = order();
        User courier = courierAt("courier@example.com", "41.037200", "28.985300");
        when(deliveryOrderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(userRepository.findEligibleCouriersForDispatch(
                Role.COURIER,
                CourierAvailabilityStatus.AVAILABLE,
                List.of(OrderStatus.ASSIGNED, OrderStatus.PICKED_UP)))
                .thenReturn(List.of(courier));
        doThrow(new ObjectOptimisticLockingFailureException(DeliveryOrder.class, 100L))
                .when(deliveryOrderRepository)
                .flush();

        assertThatThrownBy(() -> dispatchService.assignNearestEligibleCourier(100L))
                .isInstanceOf(OrderAssignmentConflictException.class)
                .hasMessage("Order assignment conflict for order: 100");
    }

    @Test
    void assignCourierToOrderAssignsAvailableCourierToPendingOrder() {
        DeliveryOrder order = order();
        User courier = courierAt("courier@example.com", "41.037200", "28.985300");
        when(deliveryOrderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(userRepository.findByIdAndRole(7L, Role.COURIER)).thenReturn(Optional.of(courier));
        when(deliveryOrderRepository.existsByCourierIdAndStatusIn(
                7L,
                List.of(OrderStatus.ASSIGNED, OrderStatus.PICKED_UP)))
                .thenReturn(false);

        DeliveryOrder assignedOrder = dispatchService.assignCourierToOrder(100L, 7L);

        assertThat(assignedOrder).isSameAs(order);
        assertThat(assignedOrder.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(assignedOrder.getCourier()).isEqualTo(courier);
        assertThat(courier.getCourierAvailabilityStatus()).isEqualTo(CourierAvailabilityStatus.ON_DELIVERY);
        verify(deliveryTimelineService).recordCourierAssigned(order);
    }

    @Test
    void assignCourierToOrderRejectsMissingOrNonCourierUser() {
        DeliveryOrder order = order();
        when(deliveryOrderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(userRepository.findByIdAndRole(8L, Role.COURIER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dispatchService.assignCourierToOrder(100L, 8L))
                .isInstanceOf(CourierNotFoundException.class)
                .hasMessage("Courier not found: 8");
    }

    @Test
    void assignCourierToOrderRejectsUnavailableCourier() {
        DeliveryOrder order = order();
        User courier = new User("courier@example.com", "hashed-password", Role.COURIER);
        when(deliveryOrderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(userRepository.findByIdAndRole(7L, Role.COURIER)).thenReturn(Optional.of(courier));

        assertThatThrownBy(() -> dispatchService.assignCourierToOrder(100L, 7L))
                .isInstanceOf(CourierNotEligibleForDispatchException.class)
                .hasMessage("Courier is not eligible for dispatch: 7 with status: UNAVAILABLE");
    }

    @Test
    void assignCourierToOrderRejectsCourierWithActiveDelivery() {
        DeliveryOrder order = order();
        User courier = courierAt("courier@example.com", "41.037200", "28.985300");
        when(deliveryOrderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(userRepository.findByIdAndRole(7L, Role.COURIER)).thenReturn(Optional.of(courier));
        when(deliveryOrderRepository.existsByCourierIdAndStatusIn(
                7L,
                List.of(OrderStatus.ASSIGNED, OrderStatus.PICKED_UP)))
                .thenReturn(true);

        assertThatThrownBy(() -> dispatchService.assignCourierToOrder(100L, 7L))
                .isInstanceOf(CourierAlreadyHasActiveDeliveryException.class)
                .hasMessage("Courier already has an active delivery: 7");
    }

    @Test
    void assignCourierToOrderRejectsNonPendingOrder() {
        DeliveryOrder order = order();
        order.cancel();
        when(deliveryOrderRepository.findById(100L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> dispatchService.assignCourierToOrder(100L, 7L))
                .isInstanceOf(OrderAssignmentNotAllowedException.class)
                .hasMessage("Order cannot be assigned from status: CANCELLED");
    }

    @Test
    void assignCourierToOrderRejectsAlreadyAssignedOrderBeforeCourierLookup() {
        DeliveryOrder order = assignedOrder();
        when(deliveryOrderRepository.findById(100L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> dispatchService.assignCourierToOrder(100L, 7L))
                .isInstanceOf(OrderAssignmentNotAllowedException.class)
                .hasMessage("Order cannot be assigned from status: ASSIGNED");
        verifyNoInteractions(userRepository);
    }

    @Test
    void assignCourierToOrderConvertsOptimisticLockFailureToAssignmentConflict() {
        DeliveryOrder order = order();
        User courier = courierAt("courier@example.com", "41.037200", "28.985300");
        when(deliveryOrderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(userRepository.findByIdAndRole(7L, Role.COURIER)).thenReturn(Optional.of(courier));
        when(deliveryOrderRepository.existsByCourierIdAndStatusIn(
                7L,
                List.of(OrderStatus.ASSIGNED, OrderStatus.PICKED_UP)))
                .thenReturn(false);
        doThrow(new ObjectOptimisticLockingFailureException(DeliveryOrder.class, 100L))
                .when(deliveryOrderRepository)
                .flush();

        assertThatThrownBy(() -> dispatchService.assignCourierToOrder(100L, 7L))
                .isInstanceOf(OrderAssignmentConflictException.class)
                .hasMessage("Order assignment conflict for order: 100");
    }

    private static User courierAt(String email, String latitude, String longitude) {
        User courier = new User(email, "hashed-password", Role.COURIER);
        courier.updateCourierAvailabilityStatus(CourierAvailabilityStatus.AVAILABLE);
        courier.updateCourierLocation(new BigDecimal(latitude), new BigDecimal(longitude));
        return courier;
    }

    private static DeliveryOrder order() {
        User customer = new User("customer@example.com", "hashed-password", Role.CUSTOMER);
        return new DeliveryOrder(
                customer,
                "Pickup",
                new BigDecimal("41.036900"),
                new BigDecimal("28.985000"),
                "Dropoff",
                new BigDecimal("40.970000"),
                new BigDecimal("29.057000"));
    }

    private static DeliveryOrder assignedOrder() {
        DeliveryOrder order = order();
        order.assignCourier(courierAt("assigned-courier@example.com", "41.037200", "28.985300"));
        return order;
    }
}
