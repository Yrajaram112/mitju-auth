package com.mitju.authservice.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(

        @Email(message = "Must be a valid email address")
        @NotBlank(message = "Email is required")
        @Size(max = 255, message = "Email too long")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 128, message = "Password must be 8–128 characters")
        @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
            message = "Password must contain at least one uppercase letter, one lowercase letter, and one digit"
        )
        String password

) {}
