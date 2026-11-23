package mx.edu.clickandtap.api.web.security;

import mx.edu.clickandtap.api.domain.Role;

public record AuthenticatedUser(Long id, String email, Role role) {
    public boolean isAdmin() { return role == Role.ADMIN; }
}