# syntax=docker/dockerfile:1.7

FROM node:20-alpine AS frontend-deps
WORKDIR /app

COPY package.json package-lock.json ./
COPY frontend/package.json ./frontend/
RUN --mount=type=cache,target=/root/.npm \
    npm ci --prefer-offline --no-audit --no-fund

FROM node:20-alpine AS frontend-build
WORKDIR /app

COPY package.json package-lock.json ./
COPY frontend ./frontend
COPY --from=frontend-deps /app/node_modules ./node_modules

RUN npm run frontend:build

FROM amazoncorretto:21-al2023 AS backend-build
WORKDIR /app

RUN dnf install -y findutils \
    && dnf clean all \
    && rm -rf /var/cache/dnf

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon --parallel --build-cache

COPY src ./src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon --parallel --build-cache

FROM amazoncorretto:21-al2023 AS jar-layers
WORKDIR /app

COPY --from=backend-build /app/build/libs/*.jar /app/app.jar
RUN java -Djarmode=layertools -jar /app/app.jar extract

FROM amazoncorretto:21-al2023
WORKDIR /app

COPY --from=jar-layers /app/dependencies/ ./
COPY --from=jar-layers /app/spring-boot-loader/ ./
COPY --from=jar-layers /app/snapshot-dependencies/ ./
COPY --from=jar-layers /app/application/ ./
COPY --from=frontend-build /app/src/main/resources/static /app/static

ENV SPRING_WEB_RESOURCES_STATIC_LOCATIONS=file:/app/static/
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENV LOG_DIR=/logs

RUN mkdir -p /logs
VOLUME ["/logs"]

EXPOSE 8080
ENTRYPOINT ["java","org.springframework.boot.loader.launch.JarLauncher"]
