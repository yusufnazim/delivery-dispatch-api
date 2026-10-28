package com.yusufnazim.deliverydispatch.dispatch;

import static com.yusufnazim.deliverydispatch.config.OpenApiConfig.SECURITY_SCHEME_NAME;

import com.yusufnazim.deliverydispatch.courier.CourierService;
import com.yusufnazim.deliverydispatch.courier.dto.OperationalCourierResponse;
import com.yusufnazim.deliverydispatch.dto.PageResponse;
import com.yusufnazim.deliverydispatch.exception.ApiErrorResponse;
import com.yusufnazim.deliverydispatch.user.CourierAvailabilityStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
            description = "Returns a page of courier users with profile, availability, and stored location details. "
                    + "Optionally filters by availability, ordered by id ascending. "
                    + "Empty and beyond-last pages return 200 with empty items and matching totals. "
                    + "This view does not determine eligibility for assignment.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Operational courier page returned"),
            @ApiResponse(
                    responseCode = "400",
                    description = "INVALID_REQUEST_PARAMETER for an unknown availability or non-integer page/size; "
                            + "VALIDATION_FAILED for out-of-range page or size",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PageResponse<OperationalCourierResponse> listOperationalCouriers(
            @Parameter(description = "Optional uppercase availability; omitted or empty means all availability states")
            @RequestParam(required = false) CourierAvailabilityStatus availability,
            @Parameter(description = "Zero-based page number; capped to keep database offsets within the supported integer range")
            @RequestParam(defaultValue = "0") @Min(0) @Max(Integer.MAX_VALUE / 100) int page,
            @Parameter(description = "Couriers per page, from 1 to 100")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return courierService.listOperationalCouriers(availability, page, size);
    }
}
