package com.yusufnazim.deliverydispatch.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ApiErrorResponse(
		String code,
		String message,
		@JsonInclude(JsonInclude.Include.ALWAYS)
		@Schema(description = "Validation failures sorted by field and message; empty for non-validation errors",
				requiredMode = Schema.RequiredMode.REQUIRED)
		List<ValidationErrorDetail> details
) {

	public ApiErrorResponse {
		details = details == null ? List.of() : List.copyOf(details);
	}

	public ApiErrorResponse(String code, String message) {
		this(code, message, List.of());
	}
}
