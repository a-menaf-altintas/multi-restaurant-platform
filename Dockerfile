# Stage 1: Build the application using Gradle
FROM gradle:8.7-jdk21 AS build
# The gradle image tag should match a version compatible with your project and JDK.
# Using gradle:8.7-jdk21 since your project uses Java 21 and Gradle 8.x.
# The README.md.pdf also mentioned gradle:8.0-jdk21. Using a slightly newer patch like 8.7 is fine.

WORKDIR /app
# Copy only the files necessary for dependency resolution first to leverage Docker cache
COPY build.gradle settings.gradle /app/
COPY backend/build.gradle /app/backend/
COPY backend/api/build.gradle /app/backend/api/
# If other modules' build.gradle files are needed for resolving api dependencies, copy them too.
# For now, assuming api's dependencies are self-contained or pulled via 'project(...)' correctly.

# Copy the rest of the source code
COPY . /app/
# Grant execution rights to gradlew
RUN chmod +x ./gradlew
# Build the application, targeting the api module's bootJar task
RUN ./gradlew :backend:api:bootJar --no-daemon

# Stage 2: Create the runtime image (This is the part you posted and looks good)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/backend/api/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]