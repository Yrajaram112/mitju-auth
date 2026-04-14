package com.mitju.authservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when login credentials are wrong, account is soft-deleted,
 * or any situation where we must not reveal *which* check failed.
 * Maps to HTTP 401 Unauthorized.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
