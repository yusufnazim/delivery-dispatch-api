package com.yusufnazim.deliverydispatch.auth.dto;

import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;

public record LoginResponse(
		String token,
		Long userId,
		String email,
		Role role
) {

	public static LoginResponse from(String token, User user) {
		return new LoginResponse(
				token,
				user.getId(),
				user.getEmail(),
				user.getRole());
	}
}
