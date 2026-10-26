package com.yusufnazim.deliverydispatch.courier;

import static com.yusufnazim.deliverydispatch.config.OpenApiConfig.SECURITY_SCHEME_NAME;

import com.yusufnazim.deliverydispatch.courier.dto.CourierAvailabilityResponse;
import com.yusufnazim.deliverydispatch.courier.dto.CourierLocationResponse;
import com.yusufnazim.deliverydispatch.courier.dto.UpdateCourierAvailabilityRequest;
import com.yusufnazim.deliverydispatch.courier.dto.UpdateCourierLocationRequest;
import com.yusufnazim.deliverydispatch.exception.ApiErrorResponse;
import com.yusufnazim.deliverydispatch.order.dto.DeliveryOrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/couriers")
@RequiredArgsConstructor
@Tag(name = "Courier workflow", description = "Courier availability, location, current assignment, pickup, and delivery actions")
@SecurityRequirement(name = SECURITY_SCHEME_NAME)
@ApiResponses({
        @ApiResponse(
                responseCode = "401",
                description = "Bearer token is missing or invalid",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Authenticated user does not have the COURIER role",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class CourierController {

    private static final String USER_ID_CLAIM = "userId";

    private final CourierService courierService;

    @GetMapping("/me/orders/current")
    @PreAuthorize("hasRole('COURIER')")
    @Operation(
            summary = "Get current delivery assignment",
            description = "Returns only the authenticated courier's ASSIGNED or PICKED_UP order, including pickup/dropoff "
                    + "addresses and coordinates. Returns no content when idle, including after delivery completion.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Current delivery assignment",
                    content = @Content(schema = @Schema(implementation = DeliveryOrderResponse.class))),
            @ApiResponse(responseCode = "204", description = "No active delivery assignment", content = @Content)
    })
    public ResponseEntity<DeliveryOrderResponse> getCurrentOrder(@AuthenticationPrincipal Jwt jwt) {
        return courierService.getCurrentOrder(userIdFrom(jwt))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PatchMapping("/me/availability")
    @PreAuthorize("hasRole('COURIER')")
    @Operation(
            summary = "Update courier availability",
            description = "Sets an idle courier to AVAILABLE or UNAVAILABLE. Changes are rejected during an active delivery. "
                    + "ON_DELIVERY is system-managed; completion releases the courier to AVAILABLE.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Courier availability updated"),
            @ApiResponse(
                    responseCode = "409",
                    description = "COURIER_AVAILABILITY_CHANGE_NOT_ALLOWED during delivery, or COURIER_UPDATE_CONFLICT "
                            + "after a concurrent write. Reload current state before retrying.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Availability value is missing or cannot be set manually",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Courier account was not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public CourierAvailabilityResponse updateAvailability(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateCourierAvailabilityRequest request) {
        return courierService.updateAvailability(userIdFrom(jwt), request.status());
    }

    @PatchMapping("/me/location")
    @PreAuthorize("hasRole('COURIER')")
    @Operation(
            summary = "Update courier location",
            description = "Stores the authenticated courier's current latitude and longitude for dispatch decisions.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Courier location updated"),
            @ApiResponse(
                    responseCode = "409",
                    description = "COURIER_UPDATE_CONFLICT after a concurrent write. Reload current state before retrying.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Latitude or longitude validation failed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Courier account was not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public CourierLocationResponse updateLocation(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateCourierLocationRequest request) {
        return courierService.updateLocation(userIdFrom(jwt), request.latitude(), request.longitude());
    }

    @PostMapping("/me/orders/{orderId}/pickup")
    @PreAuthorize("hasRole('COURIER')")
    @Operation(
            summary = "Mark an order as picked up",
            description = "Moves an assigned order to PICKED_UP for its assigned courier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order marked as picked up"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Order was not found or is assigned to another courier",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "409",
                    description = "Order cannot transition to PICKED_UP from its current status",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public DeliveryOrderResponse pickupOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId) {
        return courierService.pickupOrder(userIdFrom(jwt), orderId);
    }

    @PostMapping("/me/orders/{orderId}/deliver")
    @PreAuthorize("hasRole('COURIER')")
    @Operation(
            summary = "Mark an order as delivered",
            description = "Moves a picked-up order to DELIVERED and makes its assigned courier available.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order marked as delivered"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Order was not found or is assigned to another courier",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "409",
                    description = "INVALID_ORDER_STATUS_TRANSITION if the order is not picked up, or COURIER_UPDATE_CONFLICT "
                            + "after a concurrent courier/order write. No completion changes persist; reload before retrying.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public DeliveryOrderResponse deliverOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId) {
        return courierService.deliverOrder(userIdFrom(jwt), orderId);
    }

    private Long userIdFrom(Jwt jwt) {
        Number userId = jwt.getClaim(USER_ID_CLAIM);
        return userId.longValue();
    }
}
