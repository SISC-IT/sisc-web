-- Append-only reference revisions and SEC source facts. No legacy rewrites.
CREATE TABLE ingestion.instrument (
    instrument_id uuid PRIMARY KEY,
    name text NOT NULL,
    asset_type text NOT NULL,
    currency text NOT NULL CHECK (length(currency)=3),
    exchange text NOT NULL,
    timezone text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE ingestion.reference_record (
    record_id uuid PRIMARY KEY,
    kind text NOT NULL CHECK (kind IN ('identifier','membership','corporate_action','bar')),
    instrument_id uuid NOT NULL REFERENCES ingestion.instrument,
    source text NOT NULL,
    source_key text NOT NULL,
    published_at timestamptz NOT NULL,
    available_at timestamptz NOT NULL CHECK (available_at >= published_at),
    valid_from date NOT NULL,
    valid_to date CHECK (valid_to IS NULL OR valid_to > valid_from),
    payload jsonb NOT NULL,
    content_hash text NOT NULL,
    raw_id uuid NOT NULL REFERENCES ingestion.raw_object,
    run_id uuid NOT NULL REFERENCES ingestion.dataset_run,
    ingested_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(kind,source,source_key,available_at)
);
CREATE INDEX reference_asof_idx ON ingestion.reference_record(kind,instrument_id,available_at);
CREATE TABLE ingestion.sec_issuer (
    cik text PRIMARY KEY CHECK (cik ~ '^[0-9]{10}$'),
    active boolean NOT NULL DEFAULT true
);
CREATE TABLE ingestion.filing (
    filing_id uuid PRIMARY KEY,
    cik text NOT NULL REFERENCES ingestion.sec_issuer,
    accession_number text NOT NULL UNIQUE,
    form_type text NOT NULL,
    period_end date,
    filed_date date NOT NULL,
    accepted_at timestamptz NOT NULL,
    available_at timestamptz NOT NULL CHECK (available_at >= accepted_at),
    is_amendment boolean NOT NULL,
    primary_document text,
    raw_id uuid NOT NULL REFERENCES ingestion.raw_object,
    run_id uuid NOT NULL REFERENCES ingestion.dataset_run,
    ingested_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE ingestion.metric_catalog (
    metric_id text PRIMARY KEY,
    kind text NOT NULL CHECK (kind IN ('instant','duration')),
    unit text NOT NULL,
    definition_version text NOT NULL,
    tags jsonb NOT NULL
);
CREATE TABLE ingestion.fundamental_fact (
    fact_id uuid PRIMARY KEY,
    cik text NOT NULL REFERENCES ingestion.sec_issuer,
    metric_id text NOT NULL REFERENCES ingestion.metric_catalog,
    filing_id uuid NOT NULL REFERENCES ingestion.filing,
    source_tag text NOT NULL,
    tag_priority integer NOT NULL,
    period_start date NOT NULL,
    period_end date NOT NULL CHECK (period_end >= period_start),
    reported_fy integer,
    reported_fp text,
    value numeric NOT NULL CHECK (value NOT IN ('NaN'::numeric,'Infinity'::numeric,'-Infinity'::numeric)),
    unit text NOT NULL,
    published_at timestamptz NOT NULL,
    available_at timestamptz NOT NULL CHECK (available_at >= published_at),
    definition_version text NOT NULL,
    source_key text NOT NULL UNIQUE,
    raw_id uuid NOT NULL REFERENCES ingestion.raw_object,
    run_id uuid NOT NULL REFERENCES ingestion.dataset_run,
    ingested_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX fundamental_asof_idx ON ingestion.fundamental_fact(cik,metric_id,available_at,period_end DESC);
CREATE FUNCTION ingestion.check_fact_filing() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent ingestion.filing;
BEGIN
    SELECT * INTO parent FROM ingestion.filing WHERE filing_id=NEW.filing_id;
    IF NOT FOUND OR NEW.cik<>parent.cik OR NEW.published_at<>parent.accepted_at
        OR NEW.available_at<parent.available_at THEN
        RAISE EXCEPTION 'Fact filing identity or availability mismatch';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER fact_filing_guard BEFORE INSERT ON ingestion.fundamental_fact
    FOR EACH ROW EXECUTE FUNCTION ingestion.check_fact_filing();
CREATE FUNCTION ingestion.reject_source_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Source records are append-only; create a new sourced revision';
END $$;
CREATE TRIGGER fact_immutable BEFORE UPDATE OR DELETE ON ingestion.fundamental_fact
    FOR EACH ROW EXECUTE FUNCTION ingestion.reject_source_mutation();
CREATE TRIGGER filing_immutable BEFORE UPDATE OR DELETE ON ingestion.filing
    FOR EACH ROW EXECUTE FUNCTION ingestion.reject_source_mutation();
CREATE TRIGGER reference_immutable BEFORE UPDATE OR DELETE ON ingestion.reference_record
    FOR EACH ROW EXECUTE FUNCTION ingestion.reject_source_mutation();
INSERT INTO ingestion.schema_version(version) VALUES (2);
