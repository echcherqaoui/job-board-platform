-- Create CDC publication with partition-root publishing
-- Debezium sees all partition writes as parent table operations
CREATE PUBLICATION ${outbox_publication_name}
    FOR TABLE outbox_events
    WITH (publish_via_partition_root = true);
