package com.yusufnazim.deliverydispatch.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import org.junit.jupiter.api.Test;

class LoginResponseTest {

	@Test
	void fromMapsTokenAndUserFields() {
		User user = mock(User.class);
		when(user.getId()).thenReturn(1L);
		when(user.getEmail()).thenReturn("customer@example.com");
		when(user.getRole()).thenReturn(Role.CUSTOMER);

		LoginResponse response = LoginResponse.from("jwt-token", user);

		assertThat(response.token()).isEqualTo("jwt-token");
		assertThat(response.userId()).isEqualTo(1L);
		assertThat(response.email()).isEqualTo("customer@example.com");
		assertThat(response.role()).isEqualTo(Role.CUSTOMER);
	}
}
