# Multi-stage: Vite UI + shaded Java API on one port.
FROM node:22-alpine AS web
WORKDIR /web
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S cityflow && adduser -S cityflow -G cityflow \
    && mkdir -p /app/data /app/web/dist \
    && chown -R cityflow:cityflow /app
COPY --from=build /app/target/city-traffic-simulator-0.1.0-SNAPSHOT.jar /app/app.jar
COPY --from=web /web/dist /app/web/dist
USER cityflow
ENV CITYFLOW_PORT=8080 \
    CITYFLOW_DATA_DIR=/app/data \
    CITYFLOW_STATIC_DIR=/app/web/dist \
    CITYFLOW_CORS_ORIGINS=http://localhost:8080,http://127.0.0.1:8080
EXPOSE 8080
VOLUME ["/app/data"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
