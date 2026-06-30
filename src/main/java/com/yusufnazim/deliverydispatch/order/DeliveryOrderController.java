package com.yusufnazim.deliverydispatch.order;

import com.yusufnazim.deliverydispatch.order.dto.CreateDeliveryOrderRequest;
import com.yusufnazim.deliverydispatch.order.dto.DeliveryOrderResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class DeliveryOrderController {

    private static final String USER_ID_CLAIM = "userId";

    private final DeliveryOrderService deliveryOrderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    public DeliveryOrderResponse createOrder(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateDeliveryOrderRequest request) {
        return deliveryOrderService.createOrder(userIdFrom(jwt), request);
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<DeliveryOrderResponse> listOrders(@AuthenticationPrincipal Jwt jwt) {
        return deliveryOrderService.listCustomerOrders(userIdFrom(jwt));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public DeliveryOrderResponse getOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId) {
        return deliveryOrderService.getCustomerOrder(userIdFrom(jwt), orderId);
    }

    private Long userIdFrom(Jwt jwt) {
        Number userId = jwt.getClaim(USER_ID_CLAIM);
        return userId.longValue();
    }
}
