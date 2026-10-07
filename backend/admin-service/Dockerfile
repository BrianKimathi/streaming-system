FROM maven:3.9-eclipse-temurin-24-alpine AS builder
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests -pl admin-service -am

FROM eclipse-temurin:24-jre-alpine
WORKDIR /app
COPY --from=builder /build/admin-service/target/admin-service-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8093
ENTRYPOINT ["java", "-jar", "app.jar"]
