package com.mitju.profileservice.service;

import com.mitju.profileservice.dto.CreateProfileRequest;
import com.mitju.profileservice.dto.ProfileResponse;
import com.mitju.profileservice.dto.UpdateProfileRequest;
import com.mitju.profileservice.entity.Profile;
import com.mitju.profileservice.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;

    // ── Create ─────────────────────────────────────────────────────────

    @Transactional
    public ProfileResponse createProfile(UUID userId, CreateProfileRequest req) {
        if (profileRepository.existsByUserId(userId)) {
            throw new IllegalStateException("Profile already exists for this user");
        }

        Profile profile = Profile.builder()
                .userId(userId)
                .firstName(req.firstName())
                .lastName(req.lastName())
                .dateOfBirth(req.dateOfBirth())
                .gender(req.gender())
                .maritalStatus(req.maritalStatus() != null ? req.maritalStatus() : Profile.MaritalStatus.NEVER_MARRIED)
                .countryOfResidence(req.countryOfResidence())
                .city(req.city())
                .hometownNepal(req.hometownNepal())
                .visaStatus(req.visaStatus())
                .motherTongue(req.motherTongue())
                .communityId(req.communityId())
                .religion(req.religion())
                .highestEducation(req.highestEducation())
                .occupation(req.occupation())
                .bio(req.bio())
                .hobbies(req.hobbies())
                .lastActiveAt(Instant.now())
                .build();

        profile.setProfileCompleteness(calculateCompleteness(profile));

        return ProfileResponse.from(profileRepository.save(profile));
    }

    // ── Get my profile ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ProfileResponse getMyProfile(UUID userId) {
        Profile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found — please create one first"));
        return ProfileResponse.from(profile);
    }

    // ── Get any profile by ID ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public ProfileResponse getProfileById(UUID profileId) {
        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found"));

        // Increment view count (fire and forget — good enough for now)
        profile.setProfileViewedCount(profile.getProfileViewedCount() + 1);
        profileRepository.save(profile);

        return ProfileResponse.from(profile);
    }

    // ── Update ─────────────────────────────────────────────────────────

    @Transactional
    public ProfileResponse updateProfile(UUID userId, UpdateProfileRequest req) {
        Profile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found"));

        // Only update fields that are non-null in the request
        if (req.displayName()     != null) profile.setDisplayName(req.displayName());
        if (req.city()            != null) profile.setCity(req.city());
        if (req.stateProvince()   != null) profile.setStateProvince(req.stateProvince());
        if (req.visaStatus()      != null) profile.setVisaStatus(req.visaStatus());
        if (req.willingToRelocate() != null) profile.setWillingToRelocate(req.willingToRelocate());
        if (req.heightCm()        != null) profile.setHeightCm(req.heightCm());
        if (req.weightKg()        != null) profile.setWeightKg(req.weightKg());
        if (req.diet()            != null) profile.setDiet(req.diet());
        if (req.highestEducation()!= null) profile.setHighestEducation(req.highestEducation());
        if (req.fieldOfStudy()    != null) profile.setFieldOfStudy(req.fieldOfStudy());
        if (req.occupation()      != null) profile.setOccupation(req.occupation());
        if (req.employer()        != null) profile.setEmployer(req.employer());
        if (req.incomeRange()     != null) profile.setIncomeRange(req.incomeRange());
        if (req.bio()             != null) profile.setBio(req.bio());
        if (req.hobbies()         != null) profile.setHobbies(req.hobbies());
        if (req.familyValues()    != null) profile.setFamilyValues(req.familyValues());
        if (req.religion()        != null) profile.setReligion(req.religion());
        if (req.motherTongue()    != null) profile.setMotherTongue(req.motherTongue());
        if (req.languagesKnown()  != null) profile.setLanguagesKnown(req.languagesKnown());
        if (req.gotra()           != null) profile.setGotra(req.gotra());
        if (req.communityId()     != null) profile.setCommunityId(req.communityId());
        if (req.visibility()      != null) profile.setVisibility(req.visibility());

        profile.setProfileCompleteness(calculateCompleteness(profile));
        profile.setLastActiveAt(Instant.now());

        return ProfileResponse.from(profileRepository.save(profile));
    }

    // ── Completeness score ─────────────────────────────────────────────

    private short calculateCompleteness(Profile p) {
        int score = 0;
        if (p.getFirstName()          != null) score += 10;
        if (p.getDateOfBirth()        != null) score += 10;
        if (p.getGender()             != null) score += 5;
        if (p.getCountryOfResidence() != null) score += 5;
        if (p.getCommunityId()        != null) score += 10;
        if (p.getReligion()           != null) score += 5;
        if (p.getMotherTongue()       != null) score += 5;
        if (p.getHighestEducation()   != null) score += 10;
        if (p.getOccupation()         != null) score += 10;
        if (p.getBio()                != null && p.getBio().length() > 20) score += 15;
        if (p.getHobbies()            != null && !p.getHobbies().isEmpty()) score += 5;
        if (p.getHeightCm()           != null) score += 5;
        if (p.getVisaStatus()         != null) score += 5;
        return (short) Math.min(score, 100);
    }
}
