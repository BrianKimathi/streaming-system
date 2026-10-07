FROM eclipse-temurin:24-jre-alpine
WORKDIR /app
COPY target/admin-service-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8093
ENTRYPOINT ["java", "-jar", "app.jar"]
