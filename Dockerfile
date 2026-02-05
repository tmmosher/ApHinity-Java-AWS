# syntax=docker/dockerfile:1

FROM amazoncorretto:21 AS node-base
WORKDIR /app

RUN yum install -y curl \
    && curl -fsSL https://rpm.nodesource.com/setup_20.x | bash - \
    && yum install -y nodejs \
    && yum clean all \
    && rm -rf /var/cache/yum

FROM node-base AS frontend-deps
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

FROM node-base
WORKDIR /app

COPY --from=backend-build /app/build/libs/*.jar /app/app.jar
COPY package.json package-lock.json ./
COPY frontend ./frontend
COPY --from=frontend-deps /app/node_modules ./node_modules

ENV SPRING_WEB_RESOURCES_STATIC_LOCATIONS=file:/app/frontend/dist/,classpath:/static/

EXPOSE 8080
ENTRYPOINT ["sh","-c","npm run frontend:build && exec java -jar /app/app.jar"]
