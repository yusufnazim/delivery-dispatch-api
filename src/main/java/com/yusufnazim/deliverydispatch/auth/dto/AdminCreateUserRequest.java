package com.yusufnazim.deliverydispatch.auth.dto;

import com.yusufnazim.deliverydispatch.user.Role;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminCreateUserRequest(
        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(min = 8, max = 72)
        String password,

        @NotNull
        Role role
) {

    @AssertTrue(message = "role must be DISPATCHER or COURIER")
    public boolean isManagedUserRole() {
        return role == null || role == Role.DISPATCHER || role == Role.COURIER;
    }
}
