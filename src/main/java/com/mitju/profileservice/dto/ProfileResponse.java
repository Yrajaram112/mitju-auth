package com.mitju.profileservice.dto;

import com.mitju.profileservice.entity.Profile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ProfileResponse(
        UUID id,
        UUID userId,
        String firstName,
        String lastName,
        String displayName,
        LocalDate dateOfBirth,
        Profile.Gender gender,
        Profile.MaritalStatus maritalStatus,
        Boolean haveChildren,
        String countryOfResidence,
        String stateProvince,
        String city,
        String hometownNepal,
        Profile.VisaStatus visaStatus,
        Boolean willingToRelocate,
        String motherTongue,
        List<String> languagesKnown,
        Integer communityId,
        String religion,
        String gotra,
        Short heightCm,
        Short weightKg,
        Profile.Diet diet,
        String highestEducation,
        String fieldOfStudy,
        String occupation,
        String employer,
        String incomeRange,
        String bio,
        List<String> hobbies,
        String familyValues,
        Short profileCompleteness,
        Profile.VerificationStatus verificationStatus,
        Profile.ProfileVisibility visibility,
        Boolean isPremium,
        Instant lastActiveAt,
        Instant createdAt
) {
    public static ProfileResponse from(Profile p) {
        return new ProfileResponse(
                p.getId(), p.getUserId(),
                p.getFirstName(), p.getLastName(), p.getDisplayName(),
                p.getDateOfBirth(), p.getGender(), p.getMaritalStatus(), p.getHaveChildren(),
                p.getCountryOfResidence(), p.getStateProvince(), p.getCity(), p.getHometownNepal(),
                p.getVisaStatus(), p.getWillingToRelocate(),
                p.getMotherTongue(), p.getLanguagesKnown(),
                p.getCommunityId(), p.getReligion(), p.getGotra(),
                p.getHeightCm(), p.getWeightKg(), p.getDiet(),
                p.getHighestEducation(), p.getFieldOfStudy(),
                p.getOccupation(), p.getEmployer(), p.getIncomeRange(),
                p.getBio(), p.getHobbies(), p.getFamilyValues(),
                p.getProfileCompleteness(), p.getVerificationStatus(), p.getVisibility(),
                p.getIsPremium(), p.getLastActiveAt(), p.getCreatedAt()
        );
    }
}
