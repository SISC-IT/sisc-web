CREATE TABLE ingestion.series_catalog (
    series_id text PRIMARY KEY,
    source text NOT NULL DEFAULT 'fred' CHECK (source='fred'),
    source_series_id text NOT NULL UNIQUE,
    category text NOT NULL,
    name text,
    frequency text,
    unit text,
    seasonal_adjustment text,
    observation_start date NOT NULL,
    observation_end date,
    availability_policy text NOT NULL DEFAULT 'fred_next_day_chicago_v1'
        CHECK (availability_policy='fred_next_day_chicago_v1'),
    freshness_hours integer NOT NULL DEFAULT 30 CHECK (freshness_hours>0),
    active boolean NOT NULL DEFAULT true,
    CHECK (observation_end IS NULL OR observation_end>=observation_start)
);
-- A source can subsequently shorten an open real-time interval. Preserve each
-- observed lifetime revision instead of overwriting the earlier response.
CREATE TABLE ingestion.series_observation (
    observation_id uuid PRIMARY KEY,
    series_id text NOT NULL REFERENCES ingestion.series_catalog,
    observation_date date NOT NULL,
    vintage_date date NOT NULL,
    source_valid_through date NOT NULL CHECK (source_valid_through>=vintage_date),
    vintage_at timestamptz NOT NULL,
    published_at timestamptz,
    available_at timestamptz NOT NULL CHECK (available_at>vintage_at),
    available_to timestamptz CHECK (available_to IS NULL OR available_to>available_at),
    value numeric CHECK (value NOT IN ('NaN'::numeric,'Infinity'::numeric,'-Infinity'::numeric)),
    status text NOT NULL CHECK (status IN ('valid','missing')),
    unit text NOT NULL,
    source text NOT NULL DEFAULT 'fred' CHECK (source='fred'),
    source_key text NOT NULL UNIQUE,
    raw_id uuid NOT NULL REFERENCES ingestion.raw_object,
    run_id uuid NOT NULL REFERENCES ingestion.dataset_run,
    ingested_at timestamptz NOT NULL DEFAULT now(),
    CHECK ((status='missing' AND value IS NULL) OR (status='valid' AND value IS NOT NULL)),
    UNIQUE(series_id,observation_date,vintage_date,source_valid_through)
);
CREATE INDEX series_asof_idx ON ingestion.series_observation(series_id,available_at,observation_date);
CREATE TABLE ingestion.series_coverage (
    run_id uuid PRIMARY KEY REFERENCES ingestion.dataset_run,
    series_id text NOT NULL REFERENCES ingestion.series_catalog,
    requested_start date NOT NULL,
    requested_end date NOT NULL CHECK (requested_end>=requested_start),
    row_count bigint NOT NULL CHECK (row_count>0),
    checked_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE ingestion.series_run_observation (
    run_id uuid NOT NULL REFERENCES ingestion.series_coverage,
    observation_id uuid NOT NULL REFERENCES ingestion.series_observation,
    PRIMARY KEY(run_id,observation_id)
);
CREATE TRIGGER series_observation_immutable BEFORE UPDATE OR DELETE ON ingestion.series_observation
    FOR EACH ROW EXECUTE FUNCTION ingestion.reject_source_mutation();
CREATE TRIGGER series_coverage_immutable BEFORE UPDATE OR DELETE ON ingestion.series_coverage
    FOR EACH ROW EXECUTE FUNCTION ingestion.reject_source_mutation();
CREATE TRIGGER series_run_observation_immutable BEFORE UPDATE OR DELETE ON ingestion.series_run_observation
    FOR EACH ROW EXECUTE FUNCTION ingestion.reject_source_mutation();
INSERT INTO ingestion.schema_version(version) VALUES (3);
