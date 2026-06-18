package com.yusufnazim.deliverydispatch.exception;

public record ApiErrorResponse(
		String code,
		String message
) {
}
