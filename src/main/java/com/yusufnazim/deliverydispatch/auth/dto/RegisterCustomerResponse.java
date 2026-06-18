package com.yusufnazim.deliverydispatch.auth.dto;

import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;

public record RegisterCustomerResponse(
		Long id,
		String email,
		Role role
) {

	public static RegisterCustomerResponse from(User user) {
		return new RegisterCustomerResponse(
				user.getId(),
				user.getEmail(),
				user.getRole());
	}
}
