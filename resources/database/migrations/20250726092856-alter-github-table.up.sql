    ALTER TABLE github
    ADD COLUMN updated_at TIMESTAMP,
    ADD COLUMN topics TEXT;
--;;
    ALTER TABLE github
    ADD COLUMN topics_tsv tsvector GENERATED ALWAYS AS (to_tsvector('english', topics)) STORED;
--;;
   CREATE INDEX idx_topics_tsv ON github USING gin(topics_tsv);
--;;
    CREATE INDEX idx_topics_trgm
    ON github
    USING GIN (topics gin_trgm_ops);
