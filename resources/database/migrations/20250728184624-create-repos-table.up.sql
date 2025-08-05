CREATE EXTENSION IF NOT EXISTS citext;
--;;

CREATE TABLE repos (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    owner TEXT NOT NULL,
    repo CITEXT NOT NULL,
    url TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    stars INTEGER NOT NULL DEFAULT 0,
    topics TEXT NOT NULL DEFAULT '',
    updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    category_id INTEGER REFERENCES categories(id),
    UNIQUE (repo)
);
--;;

ALTER TABLE repos
ADD COLUMN tsv tsvector GENERATED ALWAYS AS (
  setweight(to_tsvector('english', coalesce(description, '')), 'A') ||
  setweight(to_tsvector('english', coalesce(repo, '')), 'B')
) STORED;
--;;
CREATE INDEX repos_tsv_idx ON repos USING GIN(tsv);
--;;

ALTER TABLE repos
ADD COLUMN topics_tsv tsvector GENERATED ALWAYS AS (to_tsvector('english', topics)) STORED;
--;;
CREATE INDEX repos_topics_tsv_idx ON repos USING gin(topics_tsv);
--;;

CREATE INDEX repos_repo_trgm_idx ON repos USING GIN (repo gin_trgm_ops);
--;;
CREATE INDEX repos_description_trgm_idx ON repos USING GIN (description gin_trgm_ops);
--;;
CREATE INDEX repos_topics_trgm_idx ON repos USING GIN (topics gin_trgm_ops); --;;
--;;
