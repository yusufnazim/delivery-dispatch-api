package com.yusufnazim.deliverydispatch.courier;

import com.yusufnazim.deliverydispatch.courier.dto.CourierAvailabilityResponse;
import com.yusufnazim.deliverydispatch.courier.dto.CourierLocationResponse;
import com.yusufnazim.deliverydispatch.courier.dto.UpdateCourierAvailabilityRequest;
import com.yusufnazim.deliverydispatch.courier.dto.UpdateCourierLocationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/couriers")
@RequiredArgsConstructor
public class CourierController {

    private static final String USER_ID_CLAIM = "userId";

    private final CourierService courierService;

    @PatchMapping("/me/availability")
    @PreAuthorize("hasRole('COURIER')")
    public CourierAvailabilityResponse updateAvailability(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateCourierAvailabilityRequest request) {
        return courierService.updateAvailability(userIdFrom(jwt), request.status());
    }

    @PatchMapping("/me/location")
    @PreAuthorize("hasRole('COURIER')")
    public CourierLocationResponse updateLocation(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateCourierLocationRequest request) {
        return courierService.updateLocation(userIdFrom(jwt), request.latitude(), request.longitude());
    }

    private Long userIdFrom(Jwt jwt) {
        Number userId = jwt.getClaim(USER_ID_CLAIM);
        return userId.longValue();
    }
}
