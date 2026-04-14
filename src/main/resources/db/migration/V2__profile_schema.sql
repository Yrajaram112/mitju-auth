-- V1__profile_schema.sql

CREATE SCHEMA IF NOT EXISTS profile;

CREATE TYPE profile.gender AS ENUM (
    'MALE', 'FEMALE', 'NON_BINARY', 'PREFER_NOT_TO_SAY'
);

CREATE TYPE profile.marital_status AS ENUM (
    'NEVER_MARRIED', 'DIVORCED', 'WIDOWED', 'SEPARATED'
);

CREATE TYPE profile.diet AS ENUM (
    'VEGETARIAN', 'NON_VEGETARIAN', 'VEGAN', 'JAIN', 'NO_PREFERENCE'
);

CREATE TYPE profile.verification_status AS ENUM (
    'UNVERIFIED', 'PHOTO_VERIFIED', 'ID_VERIFIED', 'FULLY_VERIFIED'
);

CREATE TYPE profile.profile_visibility AS ENUM (
    'PUBLIC', 'MEMBERS_ONLY', 'HIDDEN'
);

CREATE TYPE profile.visa_status AS ENUM (
    'CITIZEN', 'PERMANENT_RESIDENT', 'H1B', 'H4', 'F1',
    'OPT', 'GREEN_CARD_PROCESS', 'UK_ILR', 'AUSTRALIA_PR', 'OTHER', 'NOT_APPLICABLE'
);

-- Communities (seeded)
CREATE TABLE profile.communities (
    id        SMALLSERIAL PRIMARY KEY,
    name      VARCHAR(100) NOT NULL,
    country   VARCHAR(50)  NOT NULL,
    parent_id SMALLINT REFERENCES profile.communities(id),
    slug      VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE profile.profiles (
    id                   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id              UUID NOT NULL UNIQUE,          -- FK to auth.users (cross-schema, no hard FK)

    -- Identity
    first_name           VARCHAR(100) NOT NULL,
    last_name            VARCHAR(100) NOT NULL,
    display_name         VARCHAR(100),
    date_of_birth        DATE NOT NULL,
    gender               profile.gender NOT NULL,
    marital_status       profile.marital_status NOT NULL DEFAULT 'NEVER_MARRIED',
    have_children        BOOLEAN NOT NULL DEFAULT FALSE,
    children_count       SMALLINT,

    -- Culture
    community_id         SMALLINT REFERENCES profile.communities(id),
    sub_community        VARCHAR(100),
    mother_tongue        VARCHAR(50),
    languages_known      TEXT[],
    religion             VARCHAR(50),
    gotra                VARCHAR(100),

    -- Location
    country_of_residence VARCHAR(60) NOT NULL,
    state_province       VARCHAR(100),
    city                 VARCHAR(100),
    hometown_nepal       VARCHAR(100),
    willing_to_relocate  BOOLEAN DEFAULT TRUE,
    visa_status          profile.visa_status,

    -- Appearance
    height_cm            SMALLINT,
    weight_kg            SMALLINT,

    -- Lifestyle
    diet                 profile.diet,

    -- Education & Career
    highest_education    VARCHAR(100),
    field_of_study       VARCHAR(100),
    occupation           VARCHAR(150),
    employer             VARCHAR(200),
    income_range         VARCHAR(50),

    -- About
    bio                  TEXT,
    hobbies              TEXT[],
    family_values        VARCHAR(50),

    -- Meta
    profile_completeness  SMALLINT DEFAULT 0,
    verification_status   profile.verification_status DEFAULT 'UNVERIFIED',
    visibility            profile.profile_visibility DEFAULT 'MEMBERS_ONLY',
    is_premium            BOOLEAN NOT NULL DEFAULT FALSE,
    last_active_at        TIMESTAMPTZ,
    profile_viewed_count  INT DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_profile_user       ON profile.profiles(user_id);
CREATE INDEX idx_profile_community  ON profile.profiles(community_id);
CREATE INDEX idx_profile_country    ON profile.profiles(country_of_residence);
CREATE INDEX idx_profile_gender     ON profile.profiles(gender);
CREATE INDEX idx_profile_dob        ON profile.profiles(date_of_birth);
CREATE INDEX idx_profile_active     ON profile.profiles(last_active_at DESC);

-- Auto updated_at
CREATE OR REPLACE FUNCTION profile.update_updated_at()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_profiles_updated
    BEFORE UPDATE ON profile.profiles
    FOR EACH ROW EXECUTE FUNCTION profile.update_updated_at();

-- Seed communities
INSERT INTO profile.communities (name, country, slug) VALUES
    ('Brahmin',  'NEPAL', 'brahmin-np'),
    ('Chhetri',  'NEPAL', 'chhetri-np'),
    ('Newar',    'NEPAL', 'newar-np'),
    ('Gurung',   'NEPAL', 'gurung-np'),
    ('Rai',      'NEPAL', 'rai-np'),
    ('Limbu',    'NEPAL', 'limbu-np'),
    ('Tamang',   'NEPAL', 'tamang-np'),
    ('Magar',    'NEPAL', 'magar-np'),
    ('Madhesi',  'NEPAL', 'madhesi-np'),
    ('Tharu',    'NEPAL', 'tharu-np'),
    ('Sherpa',   'NEPAL', 'sherpa-np'),
    ('Brahmin',  'INDIA', 'brahmin-in'),
    ('Rajput',   'INDIA', 'rajput-in'),
    ('Kayastha', 'INDIA', 'kayastha-in'),
    ('Other',    'OTHER', 'other');
