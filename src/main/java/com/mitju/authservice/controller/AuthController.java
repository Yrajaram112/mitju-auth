package com.mitju.authservice.controller;

import com.mitju.authservice.dto.AuthResponse;
import com.mitju.authservice.dto.LoginRequest;
import com.mitju.authservice.dto.RefreshTokenRequest;
import com.mitju.authservice.dto.RegisterRequest;
import com.mitju.authservice.security.principal.UserPrincipal;
import com.mitju.authservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for authentication endpoints.
 *
 * All exception handling is delegated to GlobalExceptionHandler.
 * This controller is intentionally thin — just HTTP wiring.
 *
 * Bug fixed: Original used @AuthenticationPrincipal String userId but the
 * principal set in JwtAuthFilter is a UserPrincipal record, not a String.
 * This would cause a ClassCastException at runtime on every protected call.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login, token refresh and logout")
public class AuthController {

    private final AuthService authService;

    // ── POST /api/auth/register ────────────────────────────────────────────────

    @Operation(summary = "Register a new user account")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Account created, tokens issued"),
        @ApiResponse(responseCode = "409", description = "Email already registered"),
        @ApiResponse(responseCode = "400", description = "Validation error")
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {

        log.info("[AuthController] POST /register | email={}", request.email());
        AuthResponse response = authService.register(request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── POST /api/auth/login ───────────────────────────────────────────────────

    @Operation(summary = "Login with email and password")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Authenticated, tokens issued"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials"),
        @ApiResponse(responseCode = "403", description = "Account suspended or banned"),
        @ApiResponse(responseCode = "429", description = "Account temporarily locked")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        log.info("[AuthController] POST /login | email={}", request.email());
        return ResponseEntity.ok(authService.login(request, httpRequest));
    }

    // ── POST /api/auth/refresh ─────────────────────────────────────────────────

    @Operation(summary = "Rotate tokens using a valid refresh token")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "New token pair issued"),
        @ApiResponse(responseCode = "401", description = "Refresh token invalid, expired or revoked")
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {

        log.debug("[AuthController] POST /refresh");
        return ResponseEntity.ok(authService.refresh(request, httpRequest));
    }

    // ── POST /api/auth/logout ──────────────────────────────────────────────────

    @Operation(
        summary = "Logout — revoke the current session's refresh token",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponse(responseCode = "204", description = "Logged out successfully")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody RefreshTokenRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        log.info("[AuthController] POST /logout | userId={}", principal.userId());
        authService.logout(request.refreshToken(), principal.userId());
        return ResponseEntity.noContent().build();
    }

    // ── POST /api/auth/logout-all ──────────────────────────────────────────────

    @Operation(
        summary = "Logout from ALL devices — revoke every active session",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponse(responseCode = "204", description = "All sessions revoked")
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(
            @AuthenticationPrincipal UserPrincipal principal) {

        log.info("[AuthController] POST /logout-all | userId={}", principal.userId());
        authService.logoutAll(principal.userId());
        return ResponseEntity.noContent().build();
    }

    // ── GET /api/auth/me ───────────────────────────────────────────────────────

    @Operation(
        summary = "Get the currently authenticated user's identity",
        description = "Validates the access token and returns user identity from the JWT claims. "
                    + "No DB call — fast and useful for frontend session checks.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(
            @AuthenticationPrincipal UserPrincipal principal) {

        // No DB call intentional — returns what's in the JWT claim
        return ResponseEntity.ok(Map.of(
                "userId", principal.userId(),
                "email",  principal.email(),
                "role",   principal.role()
        ));
    }
}
