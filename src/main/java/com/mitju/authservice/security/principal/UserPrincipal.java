package com.mitju.authservice.security.principal;

import com.mitju.authservice.entity.User;

import java.util.UUID;

/**
 * Immutable security principal stored in Spring's SecurityContext after JWT validation.
 * Downstream controllers extract this via @AuthenticationPrincipal.
 *
 * Note: this is a Java record — all fields are final, equals/hashCode/toString generated.
 */
public record UserPrincipal(
        UUID userId,
        String email,
        User.UserRole role
) {
    /** Convenience: check role without importing the enum everywhere. */
    public boolean isAdmin() {
        return role == User.UserRole.ADMIN || role == User.UserRole.SUPER_ADMIN;
    }

    public boolean isFamilyMember() {
        return role == User.UserRole.FAMILY_MEMBER;
    }

    public String userIdAsString() {
        return userId.toString();
    }
}
