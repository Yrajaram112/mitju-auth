package com.mitju.authservice.exception;

import com.mitju.authservice.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a user's account status prevents login
 * (SUSPENDED, DEACTIVATED, BANNED, PENDING_VERIFICATION).
 * Maps to HTTP 403 Forbidden.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class AccountStatusException extends RuntimeException {

    private final User.AccountStatus status;

    public AccountStatusException(User.AccountStatus status) {
        super("Account access denied. Status: " + status.name());
        this.status = status;
    }

    public User.AccountStatus getStatus() {
        return status;
    }
}
