package com.yusufnazim.deliverydispatch.order;

import com.yusufnazim.deliverydispatch.order.dto.CreateDeliveryOrderRequest;
import com.yusufnazim.deliverydispatch.order.dto.DeliveryOrderResponse;
import com.yusufnazim.deliverydispatch.order.dto.OperationalOrderResponse;
import com.yusufnazim.deliverydispatch.order.exception.CustomerNotFoundException;
import com.yusufnazim.deliverydispatch.order.exception.OrderNotFoundException;
import com.yusufnazim.deliverydispatch.timeline.DeliveryTimelineService;
import com.yusufnazim.deliverydispatch.user.User;
import com.yusufnazim.deliverydispatch.user.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeliveryOrderService {

    private final DeliveryOrderRepository deliveryOrderRepository;
    private final UserRepository userRepository;
    private final DeliveryTimelineService deliveryTimelineService;

    @Transactional
    public DeliveryOrderResponse createOrder(Long customerId, CreateDeliveryOrderRequest request) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

        DeliveryOrder order = new DeliveryOrder(
                customer,
                request.pickupAddress(),
                request.pickupLatitude(),
                request.pickupLongitude(),
                request.dropoffAddress(),
                request.dropoffLatitude(),
                request.dropoffLongitude());

        DeliveryOrder savedOrder = deliveryOrderRepository.save(order);
        deliveryTimelineService.recordOrderCreated(savedOrder);

        return DeliveryOrderResponse.from(savedOrder);
    }

    @Transactional(readOnly = true)
    public DeliveryOrderResponse getCustomerOrder(Long customerId, Long orderId) {
        DeliveryOrder order = deliveryOrderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        return DeliveryOrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public List<DeliveryOrderResponse> listCustomerOrders(Long customerId) {
        return deliveryOrderRepository.findByCustomerIdOrderByCreatedAtDescIdDesc(customerId).stream()
                .map(DeliveryOrderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OperationalOrderResponse> listOperationalOrders() {
        return deliveryOrderRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(OperationalOrderResponse::from)
                .toList();
    }

    @Transactional
    public DeliveryOrderResponse cancelCustomerOrder(Long customerId, Long orderId) {
        DeliveryOrder order = deliveryOrderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        order.cancel();
        deliveryTimelineService.recordOrderCancelled(order);

        return DeliveryOrderResponse.from(order);
    }
}
