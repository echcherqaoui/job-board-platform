locals {
	retention_7_days = 604800000
	retention_1_hour    = 3600000
	standard_partitions = 3
}

# AUTH SERVICE TOPICS
# ─────────────────────────────────────────────────────────────────────────────
resource "kafka_topic" "auth_job_seeker_registered" {
  name               = "auth.user.job-seeker-registered"
  replication_factor = 1 # single broker — dev only, increase for production
  partitions         = local.standard_partitions
  config = {
    "cleanup.policy" = "delete"
    "retention.ms"   = local.retention_7_days
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "kafka_topic" "auth_recruiter_registered" {
  name               = "auth.user.recruiter-registered"
  replication_factor = 1
  partitions         = local.standard_partitions
  config = {
    "cleanup.policy" = "delete"
    "retention.ms"   = local.retention_7_days
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "kafka_topic" "auth_job_seeker_registered_dlt" {
  name               = "auth.user.job-seeker-registered-dlt"
  replication_factor = 1
  partitions         = 1  # DLT need only 1 partition
  config = {
    "cleanup.policy" = "delete"
    "retention.ms"   = local.retention_7_days
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "kafka_topic" "auth_recruiter_registered_dlt" {
  name               = "auth.user.recruiter-registered-dlt"
  replication_factor = 1
  partitions         = 1
  config = {
    "cleanup.policy" = "delete"
    "retention.ms"   = local.retention_7_days
  }

  lifecycle {
    prevent_destroy = true
  }
}

# DEBEZIUM CDC HEARTBEAT TOPICS
# ─────────────────────────────────────────────────────────────────────────────

resource "kafka_topic" "debezium_heartbeat_auth" {
  name               = "__debezium-heartbeat.auth"
  replication_factor = 1
  partitions         = 1
  config = {
    "cleanup.policy" = "delete"
    "retention.ms"   = local.retention_1_hour
  }
}
