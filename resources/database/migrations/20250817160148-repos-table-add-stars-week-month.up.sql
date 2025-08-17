ALTER TABLE repos
ADD COLUMN stars_week INTEGER NOT NULL DEFAULT 0, -- Number of stars at the beginning of the week
ADD COLUMN stars_month INTEGER NOT NULL DEFAULT 0; -- Number of stars at the beginning of the month
