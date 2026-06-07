-- ============================================================
-- V1__init_job_service.sql
-- ============================================================
CREATE TABLE jobs
(
    id               UUID PRIMARY KEY                                                                  DEFAULT gen_random_uuid(),
    recruiter_id     UUID                                                                     NOT NULL,
    title            VARCHAR(300)                                                             NOT NULL,
    description      TEXT                                                                     NOT NULL,
    requirements     TEXT,
    responsibilities TEXT,
    location         VARCHAR(200),
    work_modality    VARCHAR(30)
        CHECK (work_modality IN ('ON_SITE', 'REMOTE', 'HYBRID'))                                       DEFAULT 'ON_SITE',
    job_type         VARCHAR(30)
        CHECK (job_type IN ('FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERNSHIP', 'FREELANCE')) NOT NULL,
    experience_level VARCHAR(30)
        CHECK (experience_level IN ('JUNIOR', 'MID', 'SENIOR', 'LEAD', 'MANAGER'))            NOT NULL,
    salary_min       NUMERIC(12, 2),
    salary_max       NUMERIC(12, 2),
    currency         VARCHAR(10)                                                              NOT NULL DEFAULT 'MAD',
    status           VARCHAR(30)
        CHECK (status IN ('OPEN', 'CLOSED', 'DRAFT'))                                                  DEFAULT 'DRAFT',
    expires_at       TIMESTAMP WITH TIME ZONE,
    created_at       TIMESTAMP WITH TIME ZONE                                                 NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE                                                 NOT NULL DEFAULT NOW()
);

CREATE TABLE job_skills
(
    id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID         NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    skill  VARCHAR(100) NOT NULL
);

CREATE TABLE company_profiles
(
    recruiter_id   UUID                     NOT NULL,
    company_name   VARCHAR(100)             NOT NULL,
    company_logo   VARCHAR(255),
    last_event_id  VARCHAR(100)             NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_company_profiles PRIMARY KEY (recruiter_id)
);

-- ── Indexes ──────────────────────────────────────────────────
CREATE INDEX idx_jobs_created_at ON jobs (created_at DESC);
CREATE INDEX idx_jobs_recruiter_status ON jobs (recruiter_id, status);

CREATE INDEX idx_job_skills_job_id ON job_skills (job_id);
