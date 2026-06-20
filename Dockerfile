# Stage 1: Build the application using Maven
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Copy the pom.xml and source code
COPY pom.xml .
COPY src ./src
# Package the application (skipping tests for speed)
RUN mvn clean package -DskipTests

# Stage 2: Create the lightweight production image
FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app
# Copy only the compiled JAR from Stage 1
COPY --from=build /app/target/*.jar app.jar
# Expose the internal port Nginx is looking for
EXPOSE 8080
# Run the application

ENTRYPOINT ["java", "-jar", "app.jar"]