locals {
	retention_7_days = 604800000
	retention_1_hour    = 3600000
	standard_partitions = 3
}

# AUTH SERVICE TOPICS
# ─────────────────────────────────────────────────────────────────────────────
resource "kafka_topic" "jobboard_events_auth" {
  name               = "jobboard.events.auth"
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

resource "kafka_topic" "jobboard_events_auth_dlt" {
  name               = "jobboard.events.auth-dlt"
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

# COMPANY SERVICE TOPICS
# ─────────────────────────────────────────────────────────────────────────────
resource "kafka_topic" "jobboard_events_company" {
  name               = "jobboard.events.company"
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

resource "kafka_topic" "jobboard_events_company_dlt" {
  name               = "jobboard.events.company-dlt"
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
resource "kafka_topic" "jobboard_events_job" {
  name               = "jobboard.events.job"
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

resource "kafka_topic" "jobboard_events_job_dlt" {
  name               = "jobboard.events.job-dlt"
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


# APPLICATION SERVICE TOPICS
# ─────────────────────────────────────────────────────────────────────────────
resource "kafka_topic" "jobboard_events_application" {
  name               = "jobboard.events.application"
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

resource "kafka_topic" "jobboard_events_application_dlt" {
  name               = "jobboard.events.application-dlt"
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

resource "kafka_topic" "debezium_heartbeat_application" {
  name               = "__debezium-heartbeat.application"
  replication_factor = 1
  partitions         = 1
  config = {
    "cleanup.policy" = "delete"
    "retention.ms"   = local.retention_1_hour
  }
}