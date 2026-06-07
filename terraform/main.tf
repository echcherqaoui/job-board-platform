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

# COMPANY SERVICE TOPICS
# ─────────────────────────────────────────────────────────────────────────────
resource "kafka_topic" "company_profile_created" {
  name               = "company.profile.company-created"
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

resource "kafka_topic" "company_profile_updated" {
  name               = "company.profile.company-updated"
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

resource "kafka_topic" "company_profile_deleted" {
  name               = "company.profile.company-deleted"
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

resource "kafka_topic" "company_profile_created_dlt" {
  name               = "company.profile.company-created-dlt"
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

resource "kafka_topic" "company_profile_updated_dlt" {
  name               = "company.profile.company-updated-dlt"
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

resource "kafka_topic" "company_profile_deleted_dlt" {
  name               = "company.profile.company-deleted-dlt"
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

# JOB SERVICE TOPICS
# ─────────────────────────────────────────────────────────────────────────────
resource "kafka_topic" "job_events_job_created" {
  name               = "job.events.job-created"
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

resource "kafka_topic" "job_events_job_updated" {
  name               = "job.events.job-updated"
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

resource "kafka_topic" "job_events_job_status_changed" {
  name               = "job.events.job-status-changed"
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

resource "kafka_topic" "job_events_job_deleted" {
  name               = "job.events.job-deleted"
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

resource "kafka_topic" "job_events_job_created_dlt" {
  name               = "job.events.job-created-dlt"
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

resource "kafka_topic" "job_events_job_updated_dlt" {
  name               = "job.events.job-updated-dlt"
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

resource "kafka_topic" "job_events_job_status_changed_dlt" {
  name               = "job.events.job-status-changed-dlt"
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

resource "kafka_topic" "job_events_job_deleted_dlt" {
  name               = "job.events.job-deleted-dlt"
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

resource "kafka_topic" "debezium_heartbeat_company" {
  name               = "__debezium-heartbeat.company"
  replication_factor = 1
  partitions         = 1
  config = {
    "cleanup.policy" = "delete"
    "retention.ms"   = local.retention_1_hour
  }
}

resource "kafka_topic" "debezium_heartbeat_job" {
  name               = "__debezium-heartbeat.job"
  replication_factor = 1
  partitions         = 1
  config = {
    "cleanup.policy" = "delete"
    "retention.ms"   = local.retention_1_hour
  }
}