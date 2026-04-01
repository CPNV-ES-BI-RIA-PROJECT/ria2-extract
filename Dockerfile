# syntax=docker/dockerfile:1

# Step 1 : Build Maven
FROM maven:3.9.10-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom.xml
COPY pom.xml .

RUN mvn dependency:go-offline

# Copy the source code
COPY src ./src

# Step 2 : Validation (run tests)
FROM build AS test
RUN mvn clean test

# Step 3 : Package for production
FROM build AS package
RUN mvn clean package -DskipTests

# Step 4 : Final image (prod) lightweight
FROM eclipse-temurin:25-jre
WORKDIR /app

# Copy the jar from the build stage
COPY --from=package /app/target/*-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
