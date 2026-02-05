# syntax=docker/dockerfile:1

FROM node:20-slim AS frontend-deps
WORKDIR /app

COPY package.json package-lock.json ./
COPY frontend/package.json ./frontend/
RUN npm ci

FROM amazoncorretto:21 AS backend-build
WORKDIR /app

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

COPY src ./src

RUN ./gradlew bootJar --no-daemon

FROM node:20-slim
WORKDIR /app

RUN apt-get update \
    && apt-get install -y openjdk-21-jre-headless \
    && rm -rf /var/lib/apt/lists/*

COPY --from=backend-build /app/build/libs/*.jar /app/app.jar
COPY package.json package-lock.json ./
COPY frontend ./frontend
COPY --from=frontend-deps /app/node_modules ./node_modules

ENV SPRING_WEB_RESOURCES_STATIC_LOCATIONS=file:/app/frontend/dist/,classpath:/static/

EXPOSE 8080
ENTRYPOINT ["sh","-c","npm run frontend:build && exec java -jar /app/app.jar"]
