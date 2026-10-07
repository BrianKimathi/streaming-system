FROM eclipse-temurin:24-jre-alpine
WORKDIR /app
COPY target/subscription-service-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
