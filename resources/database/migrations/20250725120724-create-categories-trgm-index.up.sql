CREATE EXTENSION pg_trgm;
--;;
CREATE INDEX categories_name_trgm_idx ON categories USING GIN (name gin_trgm_ops);
