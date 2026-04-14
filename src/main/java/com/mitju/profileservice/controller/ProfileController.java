package com.mitju.profileservice.controller;

import com.mitju.profileservice.dto.CreateProfileRequest;
import com.mitju.profileservice.dto.ProfileResponse;
import com.mitju.profileservice.dto.UpdateProfileRequest;
import com.mitju.profileservice.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    // POST /api/profiles — create your profile (called right after register)
    @PostMapping
    public ResponseEntity<ProfileResponse> create(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody CreateProfileRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(profileService.createProfile(UUID.fromString(userId), request));
    }

    // GET /api/profiles/me — your own profile
    @GetMapping("/me")
    public ResponseEntity<ProfileResponse> getMyProfile(
            @AuthenticationPrincipal String userId) {

        return ResponseEntity.ok(
                profileService.getMyProfile(UUID.fromString(userId)));
    }

    // GET /api/profiles/{id} — view someone else's profile
    @GetMapping("/{id}")
    public ResponseEntity<ProfileResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(profileService.getProfileById(id));
    }

    // PATCH /api/profiles/me — partial update
    @PatchMapping("/me")
    public ResponseEntity<ProfileResponse> update(
            @AuthenticationPrincipal String userId,
            @RequestBody UpdateProfileRequest request) {

        return ResponseEntity.ok(
                profileService.updateProfile(UUID.fromString(userId), request));
    }

    // Error handlers
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }
}
