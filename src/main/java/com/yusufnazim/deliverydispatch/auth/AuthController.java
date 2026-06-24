package com.yusufnazim.deliverydispatch.auth;

import com.yusufnazim.deliverydispatch.auth.dto.AdminCreateUserRequest;
import com.yusufnazim.deliverydispatch.auth.dto.AdminCreateUserResponse;
import com.yusufnazim.deliverydispatch.auth.dto.LoginRequest;
import com.yusufnazim.deliverydispatch.auth.dto.LoginResponse;
import com.yusufnazim.deliverydispatch.auth.dto.RegisterCustomerRequest;
import com.yusufnazim.deliverydispatch.auth.dto.RegisterCustomerResponse;
import com.yusufnazim.deliverydispatch.user.User;
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
public class AuthController {

	private final AuthService authService;

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public RegisterCustomerResponse registerCustomer(@Valid @RequestBody RegisterCustomerRequest request) {
		User user = authService.registerCustomer(request);
		return RegisterCustomerResponse.from(user);
	}

	@PostMapping("/users")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('ADMIN')")
	public AdminCreateUserResponse createManagedUser(@Valid @RequestBody AdminCreateUserRequest request) {
		User user = authService.createManagedUser(request);
		return AdminCreateUserResponse.from(user);
	}

	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}
}
