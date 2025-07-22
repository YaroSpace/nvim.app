ALTER TABLE plugins
ADD COLUMN tsv tsvector GENERATED ALWAYS AS (
  setweight(to_tsvector('english', coalesce(repo, '')), 'B') ||
  setweight(to_tsvector('english', coalesce(description, '')), 'A')
) STORED;

--;;

CREATE INDEX plugins_tsv_idx ON plugins USING GIN(tsv);
