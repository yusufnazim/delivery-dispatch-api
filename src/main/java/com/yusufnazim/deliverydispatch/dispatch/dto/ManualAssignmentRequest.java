package com.yusufnazim.deliverydispatch.dispatch.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ManualAssignmentRequest(
        @NotNull
        @Positive
        Long courierId
) {
}
