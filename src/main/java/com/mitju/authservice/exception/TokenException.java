package com.mitju.authservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a refresh token is invalid, expired, revoked, or not found.
 * Maps to HTTP 401 Unauthorized.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class TokenException extends RuntimeException {
    public TokenException(String message) {
        super(message);
    }
}
