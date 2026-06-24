package com.yusufnazim.deliverydispatch.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yusufnazim.deliverydispatch.auth.dto.AdminCreateUserRequest;
import com.yusufnazim.deliverydispatch.auth.dto.LoginRequest;
import com.yusufnazim.deliverydispatch.auth.dto.LoginResponse;
import com.yusufnazim.deliverydispatch.auth.dto.RegisterCustomerRequest;
import com.yusufnazim.deliverydispatch.auth.exception.EmailAlreadyRegisteredException;
import com.yusufnazim.deliverydispatch.auth.exception.InvalidLoginCredentialsException;
import com.yusufnazim.deliverydispatch.security.JwtTokenService;
import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

	private final MockMvc mockMvc;
	private final ObjectMapper objectMapper;
	private final JwtTokenService jwtTokenService;

	@MockitoBean
	private AuthService authService;

	@Autowired
	AuthControllerTest(MockMvc mockMvc, ObjectMapper objectMapper, JwtTokenService jwtTokenService) {
		this.mockMvc = mockMvc;
		this.objectMapper = objectMapper;
		this.jwtTokenService = jwtTokenService;
	}

	@Test
	void registerCustomerReturnsCreatedUser() throws Exception {
		RegisterCustomerRequest request = new RegisterCustomerRequest(
				"customer@example.com",
				"StrongPass123");
		User user = user(1L, "customer@example.com", Role.CUSTOMER);
		when(authService.registerCustomer(any(RegisterCustomerRequest.class))).thenReturn(user);

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.email").value("customer@example.com"))
				.andExpect(jsonPath("$.role").value("CUSTOMER"));

		ArgumentCaptor<RegisterCustomerRequest> requestCaptor = ArgumentCaptor.forClass(RegisterCustomerRequest.class);
		verify(authService).registerCustomer(requestCaptor.capture());
		assertThat(requestCaptor.getValue().email()).isEqualTo("customer@example.com");
		assertThat(requestCaptor.getValue().password()).isEqualTo("StrongPass123");
	}

	@Test
	void registerCustomerRejectsInvalidRequest() throws Exception {
		String invalidRequest = """
				{
				  "email": "not-an-email",
				  "password": "short"
				}
				""";

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(invalidRequest))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		verifyNoInteractions(authService);
	}

	@Test
	void registerCustomerReturnsConflictForDuplicateEmail() throws Exception {
		RegisterCustomerRequest request = new RegisterCustomerRequest(
				"customer@example.com",
				"StrongPass123");
		when(authService.registerCustomer(any(RegisterCustomerRequest.class)))
				.thenThrow(new EmailAlreadyRegisteredException("customer@example.com"));

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
	}

	@Test
	void createManagedUserReturnsCreatedUserForAdmin() throws Exception {
		AdminCreateUserRequest request = new AdminCreateUserRequest(
				"dispatcher@example.com",
				"StrongPass123",
				Role.DISPATCHER);
		User user = user(2L, "dispatcher@example.com", Role.DISPATCHER);
		when(authService.createManagedUser(any(AdminCreateUserRequest.class))).thenReturn(user);

		mockMvc.perform(post("/api/v1/auth/users")
						.header("Authorization", bearerToken(Role.ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(2))
				.andExpect(jsonPath("$.email").value("dispatcher@example.com"))
				.andExpect(jsonPath("$.role").value("DISPATCHER"));

		ArgumentCaptor<AdminCreateUserRequest> requestCaptor = ArgumentCaptor.forClass(AdminCreateUserRequest.class);
		verify(authService).createManagedUser(requestCaptor.capture());
		assertThat(requestCaptor.getValue().email()).isEqualTo("dispatcher@example.com");
		assertThat(requestCaptor.getValue().password()).isEqualTo("StrongPass123");
		assertThat(requestCaptor.getValue().role()).isEqualTo(Role.DISPATCHER);
	}

	@Test
	void createManagedUserRejectsMissingBearerToken() throws Exception {
		AdminCreateUserRequest request = new AdminCreateUserRequest(
				"dispatcher@example.com",
				"StrongPass123",
				Role.DISPATCHER);

		mockMvc.perform(post("/api/v1/auth/users")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

		verifyNoInteractions(authService);
	}

	@Test
	void createManagedUserRejectsNonAdminRole() throws Exception {
		AdminCreateUserRequest request = new AdminCreateUserRequest(
				"dispatcher@example.com",
				"StrongPass123",
				Role.DISPATCHER);

		mockMvc.perform(post("/api/v1/auth/users")
						.header("Authorization", bearerToken(Role.DISPATCHER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

		verifyNoInteractions(authService);
	}

	@Test
	void createManagedUserRejectsInvalidRequest() throws Exception {
		String invalidRequest = """
				{
				  "email": "not-an-email",
				  "password": "short",
				  "role": "CUSTOMER"
				}
				""";

		mockMvc.perform(post("/api/v1/auth/users")
						.header("Authorization", bearerToken(Role.ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content(invalidRequest))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		verifyNoInteractions(authService);
	}

	@Test
	void createManagedUserReturnsConflictForDuplicateEmail() throws Exception {
		AdminCreateUserRequest request = new AdminCreateUserRequest(
				"dispatcher@example.com",
				"StrongPass123",
				Role.DISPATCHER);
		when(authService.createManagedUser(any(AdminCreateUserRequest.class)))
				.thenThrow(new EmailAlreadyRegisteredException("dispatcher@example.com"));

		mockMvc.perform(post("/api/v1/auth/users")
						.header("Authorization", bearerToken(Role.ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
	}

	@Test
	void loginReturnsTokenResponse() throws Exception {
		LoginRequest request = new LoginRequest(
				"customer@example.com",
				"StrongPass123");
		LoginResponse response = new LoginResponse(
				"jwt-token",
				7L,
				"customer@example.com",
				Role.CUSTOMER);
		when(authService.login(any(LoginRequest.class))).thenReturn(response);

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").value("jwt-token"))
				.andExpect(jsonPath("$.userId").value(7))
				.andExpect(jsonPath("$.email").value("customer@example.com"))
				.andExpect(jsonPath("$.role").value("CUSTOMER"));

		ArgumentCaptor<LoginRequest> requestCaptor = ArgumentCaptor.forClass(LoginRequest.class);
		verify(authService).login(requestCaptor.capture());
		assertThat(requestCaptor.getValue().email()).isEqualTo("customer@example.com");
		assertThat(requestCaptor.getValue().password()).isEqualTo("StrongPass123");
	}

	@Test
	void loginRejectsInvalidRequest() throws Exception {
		String invalidRequest = """
				{
				  "email": "not-an-email",
				  "password": ""
				}
				""";

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(invalidRequest))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		verifyNoInteractions(authService);
	}

	@Test
	void loginReturnsUnauthorizedForInvalidCredentials() throws Exception {
		LoginRequest request = new LoginRequest(
				"customer@example.com",
				"WrongPass123");
		when(authService.login(any(LoginRequest.class)))
				.thenThrow(new InvalidLoginCredentialsException());

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_LOGIN_CREDENTIALS"));
	}

	private User user(Long id, String email, Role role) {
		User user = org.mockito.Mockito.mock(User.class);
		when(user.getId()).thenReturn(id);
		when(user.getEmail()).thenReturn(email);
		when(user.getRole()).thenReturn(role);
		return user;
	}

	private String bearerToken(Role role) {
		return "Bearer " + jwtTokenService.generateToken(user(99L, "admin@example.com", role));
	}
}
