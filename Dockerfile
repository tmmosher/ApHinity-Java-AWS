# syntax=docker/dockerfile:1

FROM amazoncorretto:21-al2023 AS node-base
WORKDIR /app

RUN dnf install -y nodejs \
    && dnf clean all \
    && rm -rf /var/cache/dnf

FROM node-base AS frontend-deps
WORKDIR /app

COPY package.json package-lock.json ./
COPY frontend/package.json ./frontend/
RUN npm ci

FROM node-base AS frontend-build
WORKDIR /app

COPY package.json package-lock.json ./
COPY frontend ./frontend
COPY --from=frontend-deps /app/node_modules ./node_modules

ARG VITE_TURNSTILE_SITE_KEY=""
ENV VITE_TURNSTILE_SITE_KEY=${VITE_TURNSTILE_SITE_KEY}

RUN npm run frontend:build

FROM amazoncorretto:21-al2023 AS backend-build
WORKDIR /app

RUN dnf install -y findutils \
    && dnf clean all \
    && rm -rf /var/cache/dnf

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

COPY src ./src

RUN ./gradlew bootJar --no-daemon

FROM amazoncorretto:21-al2023
WORKDIR /app

COPY --from=backend-build /app/build/libs/*.jar /app/app.jar
COPY --from=frontend-build /app/src/main/resources/static /app/static

ENV SPRING_WEB_RESOURCES_STATIC_LOCATIONS=file:/app/static/

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
