package com.yusufnazim.deliverydispatch.order;

import static com.yusufnazim.deliverydispatch.config.OpenApiConfig.SECURITY_SCHEME_NAME;

import com.yusufnazim.deliverydispatch.exception.ApiErrorResponse;
import com.yusufnazim.deliverydispatch.order.dto.CreateDeliveryOrderRequest;
import com.yusufnazim.deliverydispatch.order.dto.DeliveryOrderResponse;
import com.yusufnazim.deliverydispatch.timeline.DeliveryTimelineService;
import com.yusufnazim.deliverydispatch.timeline.dto.DeliveryEventResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@Tag(name = "Customer orders", description = "Customer delivery order creation, lookup, cancellation, and timeline")
@SecurityRequirement(name = SECURITY_SCHEME_NAME)
@ApiResponses({
        @ApiResponse(
                responseCode = "401",
                description = "Bearer token is missing or invalid",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Authenticated user does not have the CUSTOMER role",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class DeliveryOrderController {

    private static final String USER_ID_CLAIM = "userId";

    private final DeliveryOrderService deliveryOrderService;
    private final DeliveryTimelineService deliveryTimelineService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
            summary = "Create a delivery order",
            description = "Creates a pending delivery order for the authenticated customer.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Delivery order created"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Address or coordinate validation failed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Authenticated customer account was not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public DeliveryOrderResponse createOrder(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateDeliveryOrderRequest request) {
        return deliveryOrderService.createOrder(userIdFrom(jwt), request);
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
            summary = "List customer orders",
            description = "Returns the authenticated customer's orders from newest to oldest.")
    @ApiResponse(responseCode = "200", description = "Customer orders returned")
    public List<DeliveryOrderResponse> listOrders(@AuthenticationPrincipal Jwt jwt) {
        return deliveryOrderService.listCustomerOrders(userIdFrom(jwt));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
            summary = "Get a customer order",
            description = "Returns an order only when it belongs to the authenticated customer.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Delivery order returned"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Order was not found or does not belong to the customer",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public DeliveryOrderResponse getOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId) {
        return deliveryOrderService.getCustomerOrder(userIdFrom(jwt), orderId);
    }

    @GetMapping("/{orderId}/timeline")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
            summary = "Get an order timeline",
            description = "Returns chronological lifecycle events for an order owned by the authenticated customer.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order timeline returned"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Order was not found or does not belong to the customer",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<DeliveryEventResponse> getOrderTimeline(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId) {
        return deliveryTimelineService.getCustomerOrderTimeline(userIdFrom(jwt), orderId);
    }

    @DeleteMapping("/{orderId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
            summary = "Cancel a delivery order",
            description = "Cancels an owned pending order before courier assignment.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Delivery order cancelled"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Order was not found or does not belong to the customer",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "409",
                    description = "Order is no longer cancellable",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public DeliveryOrderResponse cancelOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId) {
        return deliveryOrderService.cancelCustomerOrder(userIdFrom(jwt), orderId);
    }

    private Long userIdFrom(Jwt jwt) {
        Number userId = jwt.getClaim(USER_ID_CLAIM);
        return userId.longValue();
    }
}
