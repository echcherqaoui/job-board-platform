CREATE TABLE outbox_events
(
    id             UUID                     NOT NULL,
    aggregate_type VARCHAR(100)             NOT NULL,
    aggregate_id   VARCHAR(36)              NOT NULL,
    event_type     VARCHAR(100)             NOT NULL,
    payload        BYTEA                    NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE INDEX idx_outbox_event_aggregate
    ON outbox_events (aggregate_type, aggregate_id);

CREATE PUBLICATION ${outbox_publication_name}
    FOR TABLE outbox_events
    WITH (publish_via_partition_root = true);
