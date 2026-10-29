-- Additive migration. Existing public tables and readers are unchanged.
CREATE SCHEMA IF NOT EXISTS ingestion;
CREATE TABLE ingestion.schema_version (
    version integer PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE ingestion.dataset_partition (
    dataset_id text NOT NULL CHECK (dataset_id IN ('price_us_daily', 'crypto_daily')),
    partition_key text NOT NULL,
    calendar text NOT NULL,
    start_date date NOT NULL,
    end_date date,
    overlap_sessions integer NOT NULL DEFAULT 5 CHECK (overlap_sessions >= 0),
    active boolean NOT NULL DEFAULT true,
    PRIMARY KEY (dataset_id, partition_key),
    CHECK (end_date IS NULL OR end_date >= start_date)
);
CREATE TABLE ingestion.dataset_run (
    run_id uuid PRIMARY KEY,
    dataset_id text NOT NULL,
    partition_key text NOT NULL,
    requested_start date,
    requested_end date,
    status text NOT NULL CHECK (status IN ('running','success','failed','quarantined','interrupted','unverified','skipped')),
    attempt integer NOT NULL DEFAULT 1,
    code_sha text NOT NULL,
    config_hash text NOT NULL,
    started_at timestamptz NOT NULL DEFAULT now(),
    heartbeat_at timestamptz NOT NULL DEFAULT now(),
    finished_at timestamptz,
    fetched_count bigint NOT NULL DEFAULT 0,
    inserted_count bigint NOT NULL DEFAULT 0,
    rejected_count bigint NOT NULL DEFAULT 0,
    error_class text,
    metadata jsonb NOT NULL DEFAULT '{}'
);
CREATE INDEX dataset_run_status_idx ON ingestion.dataset_run(dataset_id, started_at DESC);
CREATE TABLE ingestion.dataset_watermark (
    dataset_id text NOT NULL,
    partition_key text NOT NULL,
    last_success_event_time timestamptz NOT NULL,
    last_success_run_id uuid NOT NULL REFERENCES ingestion.dataset_run,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (dataset_id, partition_key)
);
CREATE TABLE ingestion.raw_object (
    raw_id uuid PRIMARY KEY,
    run_id uuid NOT NULL REFERENCES ingestion.dataset_run,
    checksum text NOT NULL,
    object_uri text NOT NULL,
    source text NOT NULL,
    format text NOT NULL,
    fetched_at timestamptz NOT NULL,
    request_metadata jsonb NOT NULL
);
CREATE TABLE ingestion.quarantine (
    quarantine_id bigserial PRIMARY KEY,
    run_id uuid NOT NULL REFERENCES ingestion.dataset_run,
    raw_id uuid REFERENCES ingestion.raw_object,
    reasons jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
-- Immutable observations: vendor corrections create a new version.
-- This is an ingestion ledger, NOT a historical PIT price model.
CREATE TABLE ingestion.daily_bar (
    dataset_id text NOT NULL,
    partition_key text NOT NULL,
    session_date date NOT NULL,
    content_hash text NOT NULL,
    open numeric NOT NULL,
    high numeric NOT NULL,
    low numeric NOT NULL,
    close numeric NOT NULL,
    volume numeric NOT NULL,
    adjusted_close numeric,
    available_at timestamptz NOT NULL,
    ingested_at timestamptz NOT NULL DEFAULT now(),
    run_id uuid NOT NULL REFERENCES ingestion.dataset_run,
    raw_id uuid NOT NULL REFERENCES ingestion.raw_object,
    PRIMARY KEY (dataset_id, partition_key, session_date, content_hash),
    FOREIGN KEY (dataset_id, partition_key) REFERENCES ingestion.dataset_partition,
    CHECK (low > 0 AND high >= low AND open BETWEEN low AND high AND close BETWEEN low AND high),
    CHECK (volume >= 0)
);
CREATE TABLE ingestion.scheduler_heartbeat (
    scheduler_id text PRIMARY KEY,
    heartbeat_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO ingestion.schema_version(version) VALUES (1);
