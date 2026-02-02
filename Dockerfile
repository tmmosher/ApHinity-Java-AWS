# syntax=docker/dockerfile:1

FROM node:20-slim AS frontend-build
WORKDIR /app

COPY package.json package-lock.json ./
COPY frontend/package.json frontend/vite.config.ts frontend/tsconfig.json frontend/tsconfig.node.json frontend/postcss.config.cjs frontend/tailwind.config.cjs frontend/index.html frontend/.env ./frontend/
RUN npm ci

RUN mkdir -p /app/src/main/resources
COPY frontend/src ./frontend/src
RUN npm --workspace frontend run build

FROM amazoncorretto:21 AS backend-build
WORKDIR /app

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

COPY src ./src
COPY --from=frontend-build /app/src/main/resources/static ./src/main/resources/static

RUN ./gradlew bootJar --no-daemon

FROM amazoncorretto:21
WORKDIR /app

COPY --from=backend-build /app/build/libs/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
