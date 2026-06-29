package com.yusufnazim.deliverydispatch.order;

import com.yusufnazim.deliverydispatch.order.dto.CreateDeliveryOrderRequest;
import com.yusufnazim.deliverydispatch.order.dto.DeliveryOrderResponse;
import com.yusufnazim.deliverydispatch.order.exception.CustomerNotFoundException;
import com.yusufnazim.deliverydispatch.user.User;
import com.yusufnazim.deliverydispatch.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeliveryOrderService {

    private final DeliveryOrderRepository deliveryOrderRepository;
    private final UserRepository userRepository;

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

        return DeliveryOrderResponse.from(savedOrder);
    }
}
