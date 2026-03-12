.PHONY: setup test lint spec-check run load-validate frontend-e2e frontend-e2e-live benchmark-sustained benchmark-redis-cluster docker-build docker-test docker-up docker-down docker-logs

MVN ?= mvn
MAVEN_REPO ?= $(HOME)/.m2/repository
MAVEN_FLAGS := -Dmaven.repo.local=$(MAVEN_REPO)

setup:
	$(MVN) -q $(MAVEN_FLAGS) -DskipTests clean install

test:
	$(MVN) -q $(MAVEN_FLAGS) test

lint:
	$(MVN) -q $(MAVEN_FLAGS) -DskipTests validate spotless:check

spec-check:
	bash scripts/spec_check.sh

run:
	$(MVN) -q $(MAVEN_FLAGS) -DskipTests exec:java -Dexec.mainClass=org.chimera.app.ChimeraApplication

load-validate:
	bash scripts/load_validation.sh

frontend-e2e:
	cd frontend && npm run test:e2e

frontend-e2e-live:
	cd frontend && npm run test:e2e:live

benchmark-sustained:
	bash scripts/sustained_benchmark.sh

benchmark-redis-cluster:
	bash scripts/redis_cluster_stress.sh

docker-build:
	docker build -t chimera-agent-infra:local .

docker-test:
	docker run --rm -v "$(PWD):/workspace" -w /workspace maven:3.9.10-eclipse-temurin-21 mvn -q test

docker-up:
	docker compose up -d --build

docker-down:
	docker compose down --remove-orphans

docker-logs:
	docker compose logs -f api
