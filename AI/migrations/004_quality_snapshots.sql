CREATE TABLE ingestion.dq_policy (
    dataset_id text PRIMARY KEY,
    freshness_hours integer NOT NULL CHECK (freshness_hours>0),
    heartbeat_minutes integer NOT NULL DEFAULT 10 CHECK (heartbeat_minutes>0),
    success_ratio numeric NOT NULL DEFAULT 0.99 CHECK (success_ratio BETWEEN 0 AND 1),
    completeness_ratio numeric NOT NULL DEFAULT 0.995 CHECK (completeness_ratio BETWEEN 0 AND 1)
);
INSERT INTO ingestion.dq_policy(dataset_id,freshness_hours) VALUES
    ('*',30),('price_us_daily',96),('crypto_daily',30),('sec_companyfacts',30),('fred_vintage',30);
CREATE TABLE ingestion.dq_run (
    check_id uuid PRIMARY KEY,
    checked_at timestamptz NOT NULL,
    code_sha text NOT NULL,
    policy_version text NOT NULL,
    policies jsonb NOT NULL,
    result_count integer NOT NULL,
    failure_count integer NOT NULL
);
CREATE TABLE ingestion.dq_result (
    check_id uuid NOT NULL REFERENCES ingestion.dq_run,
    dataset_id text NOT NULL,
    partition_key text NOT NULL,
    rule text NOT NULL,
    severity text NOT NULL CHECK (severity IN ('warning','critical')),
    passed boolean NOT NULL,
    details jsonb NOT NULL,
    PRIMARY KEY(check_id,dataset_id,partition_key,rule)
);
CREATE TABLE ingestion.dq_incident (
    dataset_id text NOT NULL, partition_key text NOT NULL, rule text NOT NULL,
    active boolean NOT NULL, last_check_id uuid NOT NULL REFERENCES ingestion.dq_run,
    PRIMARY KEY(dataset_id,partition_key,rule)
);
-- Local alert outbox. Nothing is sent to another person or external service.
CREATE TABLE ingestion.dq_alert (
    alert_id bigserial PRIMARY KEY,
    check_id uuid NOT NULL REFERENCES ingestion.dq_run,
    dataset_id text NOT NULL, partition_key text NOT NULL, rule text NOT NULL,
    event text NOT NULL CHECK (event IN ('opened','resolved')),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE ingestion.dataset_snapshot (
    snapshot_id text PRIMARY KEY,
    content_hash text NOT NULL,
    manifest_hash text NOT NULL,
    directory text NOT NULL,
    registered_at timestamptz NOT NULL DEFAULT now()
);
CREATE TRIGGER dq_result_immutable BEFORE UPDATE OR DELETE ON ingestion.dq_result
    FOR EACH ROW EXECUTE FUNCTION ingestion.reject_source_mutation();
CREATE TRIGGER dq_run_immutable BEFORE UPDATE OR DELETE ON ingestion.dq_run
    FOR EACH ROW EXECUTE FUNCTION ingestion.reject_source_mutation();
CREATE TRIGGER dq_alert_immutable BEFORE UPDATE OR DELETE ON ingestion.dq_alert
    FOR EACH ROW EXECUTE FUNCTION ingestion.reject_source_mutation();
CREATE TRIGGER dataset_snapshot_immutable BEFORE UPDATE OR DELETE ON ingestion.dataset_snapshot
    FOR EACH ROW EXECUTE FUNCTION ingestion.reject_source_mutation();
INSERT INTO ingestion.schema_version(version) VALUES (4);
