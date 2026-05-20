-- ── Job Seeker Profile ───────────────────────────────────────
CREATE TABLE job_seeker_profiles
(
    id                   UUID PRIMARY KEY,
    first_name           VARCHAR(255),
    last_name            VARCHAR(255),
    email                VARCHAR(100) UNIQUE,
    phone                VARCHAR(100),
    location             VARCHAR(200),
    headline             VARCHAR(300),
    bio                  TEXT,
    profile_picture      VARCHAR(500),
    cv_url               VARCHAR(500),
    linkedin_url         VARCHAR(500),
    github_url           VARCHAR(500),
    portfolio_url        VARCHAR(500),
    years_experience     INTEGER,
    onboarding_completed BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- ── Job Seeker Skills ────────────────────────────────────────
CREATE TABLE job_seeker_skills
(
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id UUID         NOT NULL REFERENCES job_seeker_profiles (id) ON DELETE CASCADE,
    skill_name VARCHAR(100) NOT NULL,
    level      VARCHAR(50) CHECK (level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT'))
);
CREATE INDEX idx_job_seeker_skills_profile_id ON job_seeker_skills (profile_id);

-- ── Job Seeker Experience ────────────────────────────────────
CREATE TABLE job_seeker_experiences
(
    id           UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    profile_id   UUID         NOT NULL REFERENCES job_seeker_profiles (id) ON DELETE CASCADE,
    company_name VARCHAR(200) NOT NULL,
    job_title    VARCHAR(200) NOT NULL,
    location     VARCHAR(200),
    start_date   DATE         NOT NULL,
    end_date     DATE,
    current      BOOLEAN      NOT NULL DEFAULT FALSE,
    description  TEXT
);
CREATE INDEX idx_job_seeker_experiences_profile_id ON job_seeker_experiences (profile_id);

-- ── Job Seeker Education ─────────────────────────────────────
CREATE TABLE job_seeker_educations
(
    id          UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    profile_id  UUID         NOT NULL REFERENCES job_seeker_profiles (id) ON DELETE CASCADE,
    institution VARCHAR(200) NOT NULL,
    degree      VARCHAR(200) NOT NULL,
    field       VARCHAR(200),
    start_date  DATE         NOT NULL,
    end_date    DATE,
    current     BOOLEAN      NOT NULL DEFAULT FALSE,
    description TEXT
);
CREATE INDEX idx_job_seeker_educations_profile_id ON job_seeker_educations (profile_id);
