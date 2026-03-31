# ================================
# Stage 1: Build
# ================================
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Копируем родительский pom и модули
COPY pom.xml .
COPY shared-dto ./shared-dto
COPY auth-service ./auth-service

# Удаляем строки с модулями из родительского pom (чтобы не искать другие сервисы)
RUN sed -i '/<module>.*<\/module>/d' pom.xml

# Собираем только auth-service и его зависимости
RUN mvn -pl auth-service -am clean package -DskipTests

# ================================
# Stage 2: Runtime
# ================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /build/auth-service/target/*.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "/app/app.jar"]