.PHONY: all build clean run

all: clean build

build:
	clj -T:build uber

clean:
	clj -T:build clean

run:
	docker compose up -d --build
