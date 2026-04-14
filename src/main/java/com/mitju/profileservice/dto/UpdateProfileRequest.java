package com.mitju.profileservice.dto;

import com.mitju.profileservice.entity.Profile;

import java.util.List;

public record UpdateProfileRequest(
        String displayName,
        String city,
        String stateProvince,
        Profile.VisaStatus visaStatus,
        Boolean willingToRelocate,
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
        String religion,
        String motherTongue,
        List<String> languagesKnown,
        String gotra,
        String subCommunity,
        Integer communityId,
        Profile.ProfileVisibility visibility
) {}
