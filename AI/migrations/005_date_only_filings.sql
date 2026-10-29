-- A recovered SEC daily-feed submission can establish a publication day but
-- not an exact acceptance timestamp. Keep that timestamp unknown, never guessed.
ALTER TABLE ingestion.filing ALTER COLUMN accepted_at DROP NOT NULL;
ALTER TABLE ingestion.filing ADD COLUMN availability_basis text NOT NULL
    DEFAULT 'accepted_plus_delay' CHECK (availability_basis IN ('accepted_plus_delay','sec_feed_day_end'));
ALTER TABLE ingestion.filing ADD CONSTRAINT filing_availability_basis_guard CHECK (
    (availability_basis='accepted_plus_delay' AND accepted_at IS NOT NULL)
    OR (availability_basis='sec_feed_day_end' AND accepted_at IS NULL
        AND available_at >= ((filed_date + 1)::timestamp AT TIME ZONE 'America/New_York') + interval '60 seconds')
);
ALTER TABLE ingestion.fundamental_fact ALTER COLUMN published_at DROP NOT NULL;
CREATE OR REPLACE FUNCTION ingestion.check_fact_filing() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent ingestion.filing;
BEGIN
    SELECT * INTO parent FROM ingestion.filing WHERE filing_id=NEW.filing_id;
    IF NOT FOUND OR NEW.cik<>parent.cik OR NEW.published_at IS DISTINCT FROM parent.accepted_at
        OR NEW.available_at<parent.available_at THEN
        RAISE EXCEPTION 'Fact filing identity or availability mismatch';
    END IF;
    RETURN NEW;
END $$;
INSERT INTO ingestion.schema_version(version) VALUES (5);
