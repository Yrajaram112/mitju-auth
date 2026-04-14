package com.mitju.authservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralised exception → HTTP response mapping.
 *
 * Uses RFC 7807 ProblemDetail (Spring 6+ built-in) for consistent error shapes.
 * Format:
 * {
 *   "type":     "https://mitju.com/errors/invalid-credentials",
 *   "title":    "Unauthorized",
 *   "status":   401,
 *   "detail":   "Invalid email or password",
 *   "instance": "/api/auth/login",
 *   "timestamp": "2024-01-01T00:00:00Z"
 * }
 *
 * IMPORTANT: never include stack traces, internal class names, or SQL errors
 * in responses. Log the full detail internally instead.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String ERROR_BASE = "https://mitju.com/errors/";

    // ── Validation errors (400) ────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException ex, WebRequest request) {

        Map<String, String> fieldErrors = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .filter(e -> e instanceof FieldError)
                .map(e -> (FieldError) e)
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid",
                        (a, b) -> a  // keep first error per field
                ));

        log.debug("[GlobalExceptionHandler] Validation failed | fields={}", fieldErrors);

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create(ERROR_BASE + "validation-error"));
        problem.setTitle("Validation Error");
        problem.setDetail("One or more fields failed validation");
        problem.setProperty("errors", fieldErrors);
        problem.setProperty("timestamp", Instant.now());

        return ResponseEntity.badRequest().body(problem);
    }

    // ── Business rule exceptions ───────────────────────────────────────────────

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<ProblemDetail> handleConflict(
            ResourceAlreadyExistsException ex, WebRequest request) {

        log.warn("[GlobalExceptionHandler] Conflict | message={} path={}",
                ex.getMessage(), request.getDescription(false));

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setType(URI.create(ERROR_BASE + "resource-conflict"));
        problem.setTitle("Conflict");
        problem.setDetail(ex.getMessage());
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCredentials(
            InvalidCredentialsException ex) {

        // Log at WARN but don't log the message (which already hides which field failed)
        log.warn("[GlobalExceptionHandler] Invalid credentials attempt");

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setType(URI.create(ERROR_BASE + "invalid-credentials"));
        problem.setTitle("Unauthorized");
        problem.setDetail(ex.getMessage());
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ProblemDetail> handleLocked(AccountLockedException ex) {

        log.warn("[GlobalExceptionHandler] Account locked | lockedUntil={}", ex.getLockedUntil());

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
        problem.setType(URI.create(ERROR_BASE + "account-locked"));
        problem.setTitle("Account Locked");
        problem.setDetail(ex.getMessage());
        problem.setProperty("locked_until", ex.getLockedUntil());
        problem.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(
                        ex.getLockedUntil().getEpochSecond() - Instant.now().getEpochSecond()))
                .body(problem);
    }

    @ExceptionHandler(AccountStatusException.class)
    public ResponseEntity<ProblemDetail> handleAccountStatus(AccountStatusException ex) {

        log.warn("[GlobalExceptionHandler] Account access denied | status={}", ex.getStatus());

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setType(URI.create(ERROR_BASE + "account-forbidden"));
        problem.setTitle("Access Forbidden");
        problem.setDetail(ex.getMessage());
        problem.setProperty("account_status", ex.getStatus());
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(TokenException.class)
    public ResponseEntity<ProblemDetail> handleTokenException(TokenException ex) {

        log.warn("[GlobalExceptionHandler] Token error | message={}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setType(URI.create(ERROR_BASE + "token-invalid"));
        problem.setTitle("Unauthorized");
        problem.setDetail(ex.getMessage());
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    // ── Spring Security exceptions ─────────────────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {

        log.warn("[GlobalExceptionHandler] Access denied | message={}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setType(URI.create(ERROR_BASE + "access-denied"));
        problem.setTitle("Forbidden");
        problem.setDetail("You do not have permission to access this resource");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    // ── Catch-all (500) ────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleAll(Exception ex, WebRequest request) {

        // Log the FULL exception internally — never expose it to the client
        log.error("[GlobalExceptionHandler] Unhandled exception | path={} type={}",
                request.getDescription(false), ex.getClass().getName(), ex);

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setType(URI.create(ERROR_BASE + "internal-error"));
        problem.setTitle("Internal Server Error");
        problem.setDetail("An unexpected error occurred. Please try again later.");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
