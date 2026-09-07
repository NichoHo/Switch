FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app
# NR-11: docker-compose.yml builds this with `context: .` (the repo root) — COPY sources are
# already relative to that root. `../pom.xml` reached outside the build context entirely, which
# Docker refuses, so `docker compose up` could never have built this image.
COPY pom.xml .
COPY contracts ./contracts
COPY gateway ./gateway
COPY acquirer-sim ./acquirer-sim
RUN mvn -B package -pl gateway -am -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/gateway/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
