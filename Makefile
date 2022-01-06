all:
	./gradlew bootrun

build:
	./gradlew build

.PHONY: docker
docker:
	docker build -f docker/Dockerfile -t custom-metrics-demo:latest .
