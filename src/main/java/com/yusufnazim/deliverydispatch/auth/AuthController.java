package com.yusufnazim.deliverydispatch.auth;

import com.yusufnazim.deliverydispatch.auth.dto.RegisterCustomerRequest;
import com.yusufnazim.deliverydispatch.auth.dto.RegisterCustomerResponse;
import com.yusufnazim.deliverydispatch.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
}
