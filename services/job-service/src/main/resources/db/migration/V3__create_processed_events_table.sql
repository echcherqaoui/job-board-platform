-- ── processed_events ─────────────────────────────────────────
-- Idempotency guard for Kafka consumers.
CREATE TABLE processed_events
(
    event_id     VARCHAR(36)              NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT pk_processed_events PRIMARY KEY (event_id)
);