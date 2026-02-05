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

FROM amazoncorretto:21
WORKDIR /app

COPY --from=frontend-deps /usr/local/bin/node /usr/local/bin/node
COPY --from=frontend-deps /usr/local/bin/npm /usr/local/bin/npm
COPY --from=frontend-deps /usr/local/bin/npx /usr/local/bin/npx
COPY --from=frontend-deps /usr/local/lib/node_modules /usr/local/lib/node_modules

COPY --from=backend-build /app/build/libs/*.jar /app/app.jar
COPY package.json package-lock.json ./
COPY frontend ./frontend
COPY --from=frontend-deps /app/node_modules ./node_modules

ENV PATH=/usr/local/bin:$PATH
ENV SPRING_WEB_RESOURCES_STATIC_LOCATIONS=file:/app/frontend/dist/,classpath:/static/

EXPOSE 8080
ENTRYPOINT ["sh","-c","npm run frontend:build && exec java -jar /app/app.jar"]
