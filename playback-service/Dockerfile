FROM eclipse-temurin:24-jre-alpine
WORKDIR /app
COPY target/playback-service-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]
