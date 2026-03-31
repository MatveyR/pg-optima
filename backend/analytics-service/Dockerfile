# ================================
# Stage 1: Build
# ================================
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

COPY pom.xml .
COPY shared-dto ./shared-dto
COPY analytics-service ./analytics-service

RUN mvn -pl analytics-service -am clean package -DskipTests

# ================================
# Stage 2: Runtime
# ================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /build/analytics-service/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]