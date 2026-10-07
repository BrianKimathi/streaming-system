FROM eclipse-temurin:24-jre-alpine
RUN apk add --no-config ffmpeg || true
WORKDIR /app
COPY target/media-service-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "app.jar"]
