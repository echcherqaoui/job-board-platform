CREATE TABLE applications
(
    id           UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    job_id       UUID                     NOT NULL,
    applicant_id UUID                     NOT NULL,
    cv_url       VARCHAR(255)             NOT NULL,
    cover_letter TEXT,
    status       VARCHAR(30)              NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'REVIEWED', 'ACCEPTED', 'REJECTED', 'CANCELED')),
    submitted_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_applications_job_applicant UNIQUE (job_id, applicant_id)
);

CREATE TABLE application_status_history
(
    id             UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    application_id UUID                     NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    old_status     VARCHAR(30)
        CHECK (old_status IN ('PENDING', 'REVIEWED', 'ACCEPTED', 'REJECTED', 'CANCELED')),
    new_status     VARCHAR(30)              NOT NULL
        CHECK (new_status IN ('PENDING', 'REVIEWED', 'ACCEPTED', 'REJECTED', 'CANCELED')),
    changed_by     UUID                     NOT NULL,
    note           TEXT,
    changed_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- ── Indexes ──────────────────────────────────────────────────
CREATE INDEX idx_applications_submitted_at ON applications (submitted_at);
CREATE INDEX idx_applications_applicant_id ON applications (applicant_id);
CREATE INDEX idx_applications_job_status ON applications (job_id, status);

CREATE INDEX idx_application_status_history_application_id ON application_status_history (application_id);