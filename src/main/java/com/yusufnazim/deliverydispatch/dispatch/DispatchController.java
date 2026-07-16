package com.yusufnazim.deliverydispatch.dispatch;

import static com.yusufnazim.deliverydispatch.config.OpenApiConfig.SECURITY_SCHEME_NAME;

import com.yusufnazim.deliverydispatch.dispatch.dto.DispatchAssignmentResponse;
import com.yusufnazim.deliverydispatch.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dispatch/orders")
@RequiredArgsConstructor
@Tag(name = "Dispatch", description = "Dispatcher and administrator courier assignment operations")
@SecurityRequirement(name = SECURITY_SCHEME_NAME)
@ApiResponses({
        @ApiResponse(
                responseCode = "401",
                description = "Bearer token is missing or invalid",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Authenticated user is not a dispatcher or administrator",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class DispatchController {

    private final DispatchService dispatchService;

    @PostMapping("/{orderId}/auto-assign")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
    @Operation(
            summary = "Auto-assign an order",
            description = "Assigns the nearest eligible available courier to a pending order.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nearest eligible courier assigned"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Delivery order was not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(
                    responseCode = "409",
                    description = "Order cannot be assigned, no courier is eligible, or an assignment conflict occurred",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public DispatchAssignmentResponse autoAssignOrder(@PathVariable Long orderId) {
        return dispatchService.autoAssignOrder(orderId);
    }
}
