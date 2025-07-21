.PHONY: all build clean run

all: clean build

build:
	clj -T:build uber

clean:
	clj -T:build clean

run:
	docker compose up -d --build

db:
	docker compose -f docker-compose.yaml -f docker-compose.override.yaml up -d database

sync:
	 browser-sync start --proxy localhost:6080 -f src
