FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app
# NR-11: see gateway/Dockerfile, same fix, same reason.
COPY pom.xml .
COPY contracts ./contracts
COPY gateway ./gateway
COPY acquirer-sim ./acquirer-sim
RUN mvn -B package -pl acquirer-sim -am -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/acquirer-sim/target/*.jar app.jar
# Tracing: see gateway/Dockerfile, same agent, same reason.
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.10.0/opentelemetry-javaagent.jar /app/otel-agent.jar
ENV OTEL_SERVICE_NAME=switch-acquirer-sim
ENV OTEL_TRACES_EXPORTER=otlp
ENV OTEL_METRICS_EXPORTER=none
ENV OTEL_LOGS_EXPORTER=none
ENV OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
EXPOSE 8081
ENTRYPOINT ["sh", "-c", "if [ -n \"$OTEL_EXPORTER_OTLP_ENDPOINT\" ]; then exec java -javaagent:/app/otel-agent.jar -jar app.jar; else exec java -jar app.jar; fi"]
