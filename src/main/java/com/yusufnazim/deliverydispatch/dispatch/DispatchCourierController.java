package com.yusufnazim.deliverydispatch.dispatch;

import static com.yusufnazim.deliverydispatch.config.OpenApiConfig.SECURITY_SCHEME_NAME;

import com.yusufnazim.deliverydispatch.courier.CourierService;
import com.yusufnazim.deliverydispatch.courier.dto.OperationalCourierResponse;
import com.yusufnazim.deliverydispatch.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dispatch/couriers")
@RequiredArgsConstructor
@Tag(name = "Dispatch operations", description = "Dispatcher and administrator operational views")
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
public class DispatchCourierController {

    private final CourierService courierService;

    @GetMapping
    @PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
    @Operation(
            summary = "List operational couriers",
            description = "Returns courier profile, availability, and stored location details ordered by id.")
    @ApiResponse(responseCode = "200", description = "Operational couriers returned")
    public List<OperationalCourierResponse> listOperationalCouriers() {
        return courierService.listOperationalCouriers();
    }
}
