# Stage 1: Build the application jar
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copy the pom.xml and download dependencies first to utilize Docker caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy src directory and package the app
COPY src ./src
RUN mvn package -DskipTests

# Stage 2: Runtime image using a lightweight eclipse-temurin JRE
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Copy build jar from build stage
COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

# Expose port (default 19092, configurable via PORT or BACKEND_PORT environment variables)
EXPOSE 19092

# Run the jar
ENTRYPOINT ["java", "-jar", "app.jar"]
