# ================================
# Stage 1: Build
# ================================
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

COPY pom.xml .
# API Gateway не зависит от shared-dto (если всё же зависит, раскомментируйте строку ниже)
# COPY shared-dto ./shared-dto
COPY api-gateway ./api-gateway

# Сборка gateway (без shared-dto)
RUN mvn -pl api-gateway clean package -DskipTests

# ================================
# Stage 2: Runtime
# ================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /build/api-gateway/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]