package com.mitju.profileservice.dto;

import com.mitju.profileservice.entity.Profile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;
import java.util.List;

public record CreateProfileRequest(
        @NotBlank  String firstName,
        @NotBlank  String lastName,
        @NotNull   LocalDate dateOfBirth,
        @NotNull   Profile.Gender gender,
        Profile.MaritalStatus maritalStatus,
        @NotBlank  String countryOfResidence,
        String city,
        String hometownNepal,
        Profile.VisaStatus visaStatus,
        String motherTongue,
        Integer communityId,
        String religion,
        String highestEducation,
        String occupation,
        String bio,
        List<String> hobbies
) {}