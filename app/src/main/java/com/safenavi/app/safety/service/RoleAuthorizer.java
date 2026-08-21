package com.safenavi.app.safety.service;

import com.safenavi.app.safety.model.UserRole;

public class RoleAuthorizer {
    public void requireGovernment(UserRole role) {
        if (role != UserRole.GOVERNMENT && role != UserRole.ADMIN) {
            throw new AuthorizationException("Government authorization is required.");
        }
    }

    public void requireAdmin(UserRole role) {
        if (role != UserRole.ADMIN) {
            throw new AuthorizationException("Administrator authorization is required.");
        }
    }
}
