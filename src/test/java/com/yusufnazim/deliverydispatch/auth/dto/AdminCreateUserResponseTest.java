package com.yusufnazim.deliverydispatch.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import org.junit.jupiter.api.Test;

class AdminCreateUserResponseTest {

	@Test
	void fromMapsManagedUserFields() {
		User user = mock(User.class);
		when(user.getId()).thenReturn(2L);
		when(user.getEmail()).thenReturn("dispatcher@example.com");
		when(user.getRole()).thenReturn(Role.DISPATCHER);

		AdminCreateUserResponse response = AdminCreateUserResponse.from(user);

		assertThat(response.id()).isEqualTo(2L);
		assertThat(response.email()).isEqualTo("dispatcher@example.com");
		assertThat(response.role()).isEqualTo(Role.DISPATCHER);
	}
}
