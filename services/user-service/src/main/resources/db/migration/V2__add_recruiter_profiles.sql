CREATE TABLE recruiter_profiles
(
    id                   UUID PRIMARY KEY,
    first_name           VARCHAR(255),
    last_name            VARCHAR(255),
    email                VARCHAR(100) UNIQUE,
    company_name         VARCHAR(100),
    company_description  TEXT,
    company_logo_url     VARCHAR(255),
    company_website      VARCHAR(255),
    company_location     VARCHAR(255),
    company_size         VARCHAR(50)
        CHECK (company_size IN ('STARTUP', 'SMALL', 'MEDIUM', 'LARGE', 'ENTERPRISE')),
    onboarding_completed BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL
);