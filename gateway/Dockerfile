FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app
# NR-11: docker-compose.yml builds this with `context: .` (the repo root), so COPY sources are
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
# Tracing (agora migration, AGORA_SPEC.md section 9.2 / docs/adr/0004 in the
# agora repo): the OTel Java agent auto-instruments Spring's incoming
# requests and its outgoing acquirer-sim calls with zero source changes.
# Silent no-op when OTEL_EXPORTER_OTLP_ENDPOINT is unset, same convention
# every other service in this migration uses.
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.10.0/opentelemetry-javaagent.jar /app/otel-agent.jar
ENV OTEL_SERVICE_NAME=switch-gateway
ENV OTEL_TRACES_EXPORTER=otlp
ENV OTEL_METRICS_EXPORTER=none
ENV OTEL_LOGS_EXPORTER=none
ENV OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "if [ -n \"$OTEL_EXPORTER_OTLP_ENDPOINT\" ]; then exec java -javaagent:/app/otel-agent.jar -jar app.jar; else exec java -jar app.jar; fi"]
