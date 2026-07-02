# Use a lightweight JRE 21 image for running the Spring Boot app
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the compiled JAR file from the Gradle build output
COPY build/libs/*.jar app.jar

# Expose the default Spring Boot port
EXPOSE 8080

# Start the application
ENTRYPOINT ["java", "-jar", "app.jar"]
