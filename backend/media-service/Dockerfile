FROM maven:3.9-eclipse-temurin-24-alpine AS builder
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests -pl media-service -am

FROM eclipse-temurin:24-jre-alpine
RUN apk add --no-cache ffmpeg
WORKDIR /app
COPY --from=builder /build/media-service/target/media-service-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "app.jar"]
