FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build
COPY pom.xml .
COPY common ./common
COPY playback-service ./playback-service
RUN mvn clean package -DskipTests -pl playback-service -am

FROM eclipse-temurin:24-jre-alpine
WORKDIR /app
COPY --from=builder /build/playback-service/target/playback-service-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]
