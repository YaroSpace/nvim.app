CREATE TABLE repositories (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    owner TEXT NOT NULL,
    url TEXT NOT NULL,
    description TEXT,
    stars INTEGER,
    topics TEXT,
    updated TIMESTAMP,
    UNIQUE (owner, name)
);
