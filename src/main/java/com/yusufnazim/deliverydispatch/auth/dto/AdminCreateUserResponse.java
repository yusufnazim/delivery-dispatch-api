package com.yusufnazim.deliverydispatch.auth.dto;

import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;

public record AdminCreateUserResponse(
		Long id,
		String email,
		Role role
) {

	public static AdminCreateUserResponse from(User user) {
		return new AdminCreateUserResponse(
				user.getId(),
				user.getEmail(),
				user.getRole());
	}
}
