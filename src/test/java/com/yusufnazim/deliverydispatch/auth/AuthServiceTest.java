package com.yusufnazim.deliverydispatch.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yusufnazim.deliverydispatch.auth.dto.AdminCreateUserRequest;
import com.yusufnazim.deliverydispatch.auth.dto.LoginRequest;
import com.yusufnazim.deliverydispatch.auth.dto.LoginResponse;
import com.yusufnazim.deliverydispatch.auth.dto.RegisterCustomerRequest;
import com.yusufnazim.deliverydispatch.auth.exception.EmailAlreadyRegisteredException;
import com.yusufnazim.deliverydispatch.auth.exception.InvalidManagedUserRoleException;
import com.yusufnazim.deliverydispatch.auth.exception.InvalidLoginCredentialsException;
import com.yusufnazim.deliverydispatch.security.JwtTokenService;
import com.yusufnazim.deliverydispatch.user.CourierAvailabilityStatus;
import com.yusufnazim.deliverydispatch.user.Role;
import com.yusufnazim.deliverydispatch.user.User;
import com.yusufnazim.deliverydispatch.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerCustomerCreatesCustomerWithHashedPassword() {
        RegisterCustomerRequest request = new RegisterCustomerRequest(
                "Customer@Example.COM ",
                "StrongPass123");
        when(userRepository.existsByEmail("customer@example.com")).thenReturn(false);
        when(passwordEncoder.encode("StrongPass123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User registeredUser = authService.registerCustomer(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getEmail()).isEqualTo("customer@example.com");
        assertThat(savedUser.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(savedUser.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(savedUser.getCourierAvailabilityStatus()).isNull();
        assertThat(registeredUser).isSameAs(savedUser);
    }

    @Test
    void registerCustomerRejectsDuplicateEmail() {
        RegisterCustomerRequest request = new RegisterCustomerRequest(
                "customer@example.com",
                "StrongPass123");
        when(userRepository.existsByEmail("customer@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.registerCustomer(request))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessageContaining("customer@example.com");

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void createManagedUserCreatesDispatcherWithHashedPassword() {
        AdminCreateUserRequest request = new AdminCreateUserRequest(
                "Dispatcher@Example.COM ",
                "StrongPass123",
                Role.DISPATCHER);
        when(userRepository.existsByEmail("dispatcher@example.com")).thenReturn(false);
        when(passwordEncoder.encode("StrongPass123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User createdUser = authService.createManagedUser(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getEmail()).isEqualTo("dispatcher@example.com");
        assertThat(savedUser.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(savedUser.getRole()).isEqualTo(Role.DISPATCHER);
        assertThat(savedUser.getCourierAvailabilityStatus()).isNull();
        assertThat(createdUser).isSameAs(savedUser);
    }

    @Test
    void createManagedUserCreatesCourierWithRequestedRole() {
        AdminCreateUserRequest request = new AdminCreateUserRequest(
                "courier@example.com",
                "StrongPass123",
                Role.COURIER);
        when(userRepository.existsByEmail("courier@example.com")).thenReturn(false);
        when(passwordEncoder.encode("StrongPass123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User createdUser = authService.createManagedUser(request);

        assertThat(createdUser.getEmail()).isEqualTo("courier@example.com");
        assertThat(createdUser.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(createdUser.getRole()).isEqualTo(Role.COURIER);
        assertThat(createdUser.getCourierAvailabilityStatus()).isEqualTo(CourierAvailabilityStatus.UNAVAILABLE);
    }

    @Test
    void createManagedUserRejectsDuplicateEmail() {
        AdminCreateUserRequest request = new AdminCreateUserRequest(
                "dispatcher@example.com",
                "StrongPass123",
                Role.DISPATCHER);
        when(userRepository.existsByEmail("dispatcher@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.createManagedUser(request))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessageContaining("dispatcher@example.com");

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void createManagedUserRejectsCustomerRole() {
        AdminCreateUserRequest request = new AdminCreateUserRequest(
                "customer@example.com",
                "StrongPass123",
                Role.CUSTOMER);

        assertThatThrownBy(() -> authService.createManagedUser(request))
                .isInstanceOf(InvalidManagedUserRoleException.class)
                .hasMessageContaining("CUSTOMER");

        verify(userRepository, never()).existsByEmail(any());
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginReturnsTokenAndUserDetailsForValidCredentials() {
        LoginRequest request = new LoginRequest(
                "Customer@Example.COM ",
                "StrongPass123");
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(user.getEmail()).thenReturn("customer@example.com");
        when(user.getPasswordHash()).thenReturn("hashed-password");
        when(user.getRole()).thenReturn(Role.CUSTOMER);
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("StrongPass123", "hashed-password")).thenReturn(true);
        when(jwtTokenService.generateToken(user)).thenReturn("jwt-token");

        LoginResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.userId()).isEqualTo(7L);
        assertThat(response.email()).isEqualTo("customer@example.com");
        assertThat(response.role()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void loginRejectsUnknownEmail() {
        LoginRequest request = new LoginRequest(
                "missing@example.com",
                "StrongPass123");
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidLoginCredentialsException.class)
                .hasMessage("Invalid email or password");

        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtTokenService, never()).generateToken(any());
    }

    @Test
    void loginRejectsInvalidPassword() {
        LoginRequest request = new LoginRequest(
                "customer@example.com",
                "WrongPass123");
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getPasswordHash()).thenReturn("hashed-password");
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPass123", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidLoginCredentialsException.class)
                .hasMessage("Invalid email or password");

        verify(jwtTokenService, never()).generateToken(any());
    }
}
