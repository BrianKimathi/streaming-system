FROM maven:3.9-eclipse-temurin-24-alpine AS builder
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests -pl analytics-service -am

FROM eclipse-temurin:24-jre-alpine
WORKDIR /app
COPY --from=builder /build/analytics-service/target/analytics-service-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8091
ENTRYPOINT ["java", "-jar", "app.jar"]
