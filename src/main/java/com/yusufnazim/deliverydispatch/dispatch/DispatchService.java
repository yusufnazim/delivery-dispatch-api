package com.yusufnazim.deliverydispatch.dispatch;

import com.yusufnazim.deliverydispatch.courier.exception.CourierNotFoundException;
import com.yusufnazim.deliverydispatch.dispatch.exception.CourierNotEligibleForDispatchException;
import com.yusufnazim.deliverydispatch.dispatch.exception.NoEligibleCourierException;
import com.yusufnazim.deliverydispatch.order.DeliveryOrder;
import com.yusufnazim.deliverydispatch.order.DeliveryOrderRepository;
import com.yusufnazim.deliverydispatch.order.OrderStatus;
import com.yusufnazim.deliverydispatch.order.exception.OrderAssignmentNotAllowedException;
import com.yusufnazim.deliverydispatch.order.exception.OrderNotFoundException;
import com.yusufnazim.deliverydispatch.user.CourierAvailabilityStatus;
import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import com.yusufnazim.deliverydispatch.user.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DispatchService {

    private final DeliveryOrderRepository deliveryOrderRepository;
    private final UserRepository userRepository;
    private final HaversineDistanceCalculator distanceCalculator;

    @Transactional(readOnly = true)
    public List<User> findEligibleCouriers() {
        return userRepository.findEligibleCouriersForDispatch(Role.COURIER, CourierAvailabilityStatus.AVAILABLE);
    }

    @Transactional(readOnly = true)
    public Optional<User> findNearestEligibleCourier(BigDecimal pickupLatitude, BigDecimal pickupLongitude) {
        Objects.requireNonNull(pickupLatitude, "pickupLatitude must not be null");
        Objects.requireNonNull(pickupLongitude, "pickupLongitude must not be null");

        return findEligibleCouriers().stream()
                .min((firstCourier, secondCourier) -> Double.compare(
                        distanceToPickup(firstCourier, pickupLatitude, pickupLongitude),
                        distanceToPickup(secondCourier, pickupLatitude, pickupLongitude)));
    }

    @Transactional
    public DeliveryOrder assignNearestEligibleCourier(Long orderId) {
        DeliveryOrder order = findPendingOrderForAssignment(orderId);

        User courier = findNearestEligibleCourier(order.getPickupLatitude(), order.getPickupLongitude())
                .orElseThrow(() -> new NoEligibleCourierException(orderId));

        assignCourier(order, courier);

        return order;
    }

    @Transactional
    public DeliveryOrder assignCourierToOrder(Long orderId, Long courierId) {
        DeliveryOrder order = findPendingOrderForAssignment(orderId);
        User courier = userRepository.findByIdAndRole(courierId, Role.COURIER)
                .orElseThrow(() -> new CourierNotFoundException(courierId));
        validateCourierEligibleForDispatch(courierId, courier);

        assignCourier(order, courier);

        return order;
    }

    private DeliveryOrder findPendingOrderForAssignment(Long orderId) {
        DeliveryOrder order = deliveryOrderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new OrderAssignmentNotAllowedException(order.getStatus());
        }
        return order;
    }

    private void assignCourier(DeliveryOrder order, User courier) {
        order.assignCourier(courier);
        courier.updateCourierAvailabilityStatus(CourierAvailabilityStatus.ON_DELIVERY);
    }

    private void validateCourierEligibleForDispatch(Long courierId, User courier) {
        if (courier.getCourierAvailabilityStatus() != CourierAvailabilityStatus.AVAILABLE) {
            throw new CourierNotEligibleForDispatchException(courierId, courier.getCourierAvailabilityStatus());
        }
    }

    private double distanceToPickup(User courier, BigDecimal pickupLatitude, BigDecimal pickupLongitude) {
        return distanceCalculator.distanceInKilometers(
                pickupLatitude,
                pickupLongitude,
                courier.getCourierLatitude(),
                courier.getCourierLongitude());
    }
}
