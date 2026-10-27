package com.yusufnazim.deliverydispatch.exception;

import io.swagger.v3.oas.annotations.media.Schema;

public record ValidationErrorDetail(
        @Schema(description = "Request field or parameter name; _request for request-wide errors", example = "email",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String field,
        @Schema(description = "Validation message without the rejected value", example = "must not be blank",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String message
) {
}
