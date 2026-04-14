package com.mitju.profileservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "profiles", schema = "profile")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Profile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID userId;                      // matches auth.users.id (no hard FK across services)

    // ── Identity ──────────────────────────────────────────────────────
    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private String displayName;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private MaritalStatus maritalStatus = MaritalStatus.NEVER_MARRIED;

    @Builder.Default
    private Boolean haveChildren = false;
    private Short childrenCount;

    // ── Culture ───────────────────────────────────────────────────────
    private Integer communityId;
    private String subCommunity;
    private String motherTongue;

    @Column(columnDefinition = "text[]")
    private List<String> languagesKnown;

    private String religion;
    private String gotra;

    // ── Location ──────────────────────────────────────────────────────
    @Column(nullable = false)
    private String countryOfResidence;

    private String stateProvince;
    private String city;
    private String hometownNepal;

    @Builder.Default
    private Boolean willingToRelocate = true;

    @Enumerated(EnumType.STRING)
    private VisaStatus visaStatus;

    // ── Appearance ────────────────────────────────────────────────────
    private Short heightCm;
    private Short weightKg;

    // ── Lifestyle ─────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    private Diet diet;

    // ── Education & Career ────────────────────────────────────────────
    private String highestEducation;
    private String fieldOfStudy;
    private String occupation;
    private String employer;
    private String incomeRange;

    // ── About ─────────────────────────────────────────────────────────
    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(columnDefinition = "text[]")
    private List<String> hobbies;

    private String familyValues;

    // ── Meta ──────────────────────────────────────────────────────────
    @Builder.Default
    private Short profileCompleteness = 0;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.UNVERIFIED;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ProfileVisibility visibility = ProfileVisibility.MEMBERS_ONLY;

    @Builder.Default
    private Boolean isPremium = false;

    private Instant lastActiveAt;

    @Builder.Default
    private Integer profileViewedCount = 0;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    // ── Enums ─────────────────────────────────────────────────────────

    public enum Gender { MALE, FEMALE, NON_BINARY, PREFER_NOT_TO_SAY }
    public enum MaritalStatus { NEVER_MARRIED, DIVORCED, WIDOWED, SEPARATED }
    public enum Diet { VEGETARIAN, NON_VEGETARIAN, VEGAN, JAIN, NO_PREFERENCE }
    public enum VisaStatus {
        CITIZEN, PERMANENT_RESIDENT, H1B, H4, F1,
        OPT, GREEN_CARD_PROCESS, UK_ILR, AUSTRALIA_PR, OTHER, NOT_APPLICABLE
    }
    public enum VerificationStatus { UNVERIFIED, PHOTO_VERIFIED, ID_VERIFIED, FULLY_VERIFIED }
    public enum ProfileVisibility { PUBLIC, MEMBERS_ONLY, HIDDEN }
}
