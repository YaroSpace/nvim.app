CREATE TABLE app (
    id SERIAL PRIMARY KEY,
    data JSONB NOT NULL DEFAULT '{}',
    settings JSONB NOT NULL DEFAULT '{}'
);

--;;

insert into app (id, data, settings) values (1, '{}', '{}');
