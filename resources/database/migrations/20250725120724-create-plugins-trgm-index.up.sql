CREATE EXTENSION pg_trgm;
--;;
CREATE INDEX plugins_repo_trgm_idx ON plugins USING GIN (repo gin_trgm_ops);
--;;
CREATE INDEX plugins_description_trgm_idx ON plugins USING GIN (description gin_trgm_ops);
--;;
CREATE INDEX categories_name_trgm_idx ON categories USING GIN (name gin_trgm_ops);
