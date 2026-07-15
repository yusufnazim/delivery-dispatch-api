package com.yusufnazim.deliverydispatch.auth;

import static com.yusufnazim.deliverydispatch.config.OpenApiConfig.SECURITY_SCHEME_NAME;

import com.yusufnazim.deliverydispatch.auth.dto.AdminCreateUserRequest;
import com.yusufnazim.deliverydispatch.auth.dto.AdminCreateUserResponse;
import com.yusufnazim.deliverydispatch.auth.dto.LoginRequest;
import com.yusufnazim.deliverydispatch.auth.dto.LoginResponse;
import com.yusufnazim.deliverydispatch.auth.dto.RegisterCustomerRequest;
import com.yusufnazim.deliverydispatch.auth.dto.RegisterCustomerResponse;
import com.yusufnazim.deliverydispatch.exception.ApiErrorResponse;
import com.yusufnazim.deliverydispatch.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Account registration, login, and administrator-managed users")
public class AuthController {

	private final AuthService authService;

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(
			summary = "Register a customer",
			description = "Creates a public customer account with a unique email address.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Customer account created"),
			@ApiResponse(
					responseCode = "400",
					description = "Request validation failed",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(
					responseCode = "409",
					description = "Email address is already registered",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public RegisterCustomerResponse registerCustomer(@Valid @RequestBody RegisterCustomerRequest request) {
		User user = authService.registerCustomer(request);
		return RegisterCustomerResponse.from(user);
	}

	@PostMapping("/users")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(
			summary = "Create a managed user",
			description = "Creates a dispatcher or courier account. Requires the ADMIN role.",
			security = @SecurityRequirement(name = SECURITY_SCHEME_NAME))
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Managed user account created"),
			@ApiResponse(
					responseCode = "400",
					description = "Request validation failed or role is unsupported",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(
					responseCode = "401",
					description = "Bearer token is missing or invalid",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(
					responseCode = "403",
					description = "Authenticated user does not have the ADMIN role",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(
					responseCode = "409",
					description = "Email address is already registered",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public AdminCreateUserResponse createManagedUser(@Valid @RequestBody AdminCreateUserRequest request) {
		User user = authService.createManagedUser(request);
		return AdminCreateUserResponse.from(user);
	}

	@PostMapping("/login")
	@Operation(
			summary = "Log in",
			description = "Validates account credentials and returns a bearer JWT for protected endpoints.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Credentials accepted and JWT issued"),
			@ApiResponse(
					responseCode = "400",
					description = "Request validation failed",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(
					responseCode = "401",
					description = "Email address or password is invalid",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}
}
