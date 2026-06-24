package com.yusufnazim.deliverydispatch.auth.exception;

import com.yusufnazim.deliverydispatch.user.Role;

public class InvalidManagedUserRoleException extends RuntimeException {

    public InvalidManagedUserRoleException(Role role) {
        super("Managed user role must be DISPATCHER or COURIER: " + role);
    }
}
