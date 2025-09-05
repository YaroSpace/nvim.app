ssh_command = ssh -fNT nvim.app
repl_port = 7000

define confirm
	@tput setaf 3; read -p "$(1) (y/N) " ans; tput sgr0; [ "$$ans" = "y" ]
endef

.PHONY: all build clean run

all: clean css build deploy

build:
	clj -T:build uber

clean:
	clj -T:build clean

deploy:
	$(call confirm,Commit changes?)
	git add .
	git commit --amend --no-edit

	$(call confirm,Push changes?)
	git push --force

lint:
	clj-kondo --lint src

run:
	docker compose up -d --build

db:
	docker compose -f docker-compose.yaml -f docker-compose.override.yaml up -d database

css:
	npx @tailwindcss/cli -i resources/public/css/in.css -o resources/public/css/out.css --minify

css_watch:
	npx @tailwindcss/cli -i resources/public/css/in.css -o resources/public/css/out.css --minify --watch

sync:
	browser-sync start --proxy localhost:6080 -f src

remote: 
	@$(ssh_command)

	@for i in $$(seq 1 30); do \
		if nc -z localhost $(repl_port); then \
			echo "Port $(repl_port) is now available, connecting..."; \
			break; \
		fi; \
		echo "Waiting for port 7000 to become available... ($$i/30)"; \
		sleep 1; \
	done

	@clojure -T:nrebel :port $(repl_port)
	@pkill -f "$(ssh_command)"

test_watch:
	clojure -X:test/run:test/watch
