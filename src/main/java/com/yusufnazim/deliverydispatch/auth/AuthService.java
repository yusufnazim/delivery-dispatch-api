package com.yusufnazim.deliverydispatch.auth;

import com.yusufnazim.deliverydispatch.auth.dto.AdminCreateUserRequest;
import com.yusufnazim.deliverydispatch.auth.dto.LoginRequest;
import com.yusufnazim.deliverydispatch.auth.dto.LoginResponse;
import com.yusufnazim.deliverydispatch.auth.dto.RegisterCustomerRequest;
import com.yusufnazim.deliverydispatch.auth.exception.EmailAlreadyRegisteredException;
import com.yusufnazim.deliverydispatch.auth.exception.InvalidManagedUserRoleException;
import com.yusufnazim.deliverydispatch.auth.exception.InvalidLoginCredentialsException;
import com.yusufnazim.deliverydispatch.security.JwtTokenService;
import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import com.yusufnazim.deliverydispatch.user.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    @Transactional
    public User registerCustomer(RegisterCustomerRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }

        String passwordHash = passwordEncoder.encode(request.password());
        User user = new User(email, passwordHash, Role.CUSTOMER);

        return userRepository.save(user);
    }

    @Transactional
    public User createManagedUser(AdminCreateUserRequest request) {
        Role role = request.role();
        if (!isManagedUserRole(role)) {
            throw new InvalidManagedUserRoleException(role);
        }

        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }

        String passwordHash = passwordEncoder.encode(request.password());
        User user = new User(email, passwordHash, role);

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidLoginCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidLoginCredentialsException();
        }

        String token = jwtTokenService.generateToken(user);
        return LoginResponse.from(token, user);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isManagedUserRole(Role role) {
        return role == Role.DISPATCHER || role == Role.COURIER;
    }
}
