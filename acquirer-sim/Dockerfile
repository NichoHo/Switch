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
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
