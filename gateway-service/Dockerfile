# syntax=docker/dockerfile:1
#
# Built from the repository root, not from gateway-service/: the image bundles the SPA so the
# browser talks to a single origin (ADR 0006).
#   docker build -f gateway-service/Dockerfile .

FROM node:24-alpine AS frontend
WORKDIR /app

COPY trackly-client/package.json trackly-client/package-lock.json ./
RUN npm ci

COPY trackly-client/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-25-alpine AS builder
WORKDIR /app

COPY gateway-service/pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

ARG GIT_SHA=unknown
ARG BUILD_NUMBER=0
ARG BUILD_URL=local

COPY gateway-service/src ./src
# Spring serves classpath:/static/**, so the built SPA becomes part of the jar.
COPY --from=frontend /app/dist/trackly-client/browser/ ./src/main/resources/static/

RUN --mount=type=cache,target=/root/.m2 mvn -B -q clean package -DskipTests \
      -Dgit.commit.id="$GIT_SHA" \
      -Dci.build.number="$BUILD_NUMBER" \
      -Dci.build.url="$BUILD_URL"

RUN java -Djarmode=tools -jar target/*.jar extract --layers --destination extracted && \
    mv extracted/application/*.jar extracted/application/app.jar

FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app

RUN apk add --no-cache dumb-init=1.2.5-r3 && \
    addgroup -S appuser && \
    adduser -S -s /bin/false -G appuser appuser && \
    chown -R appuser:appuser /app

USER appuser

COPY --from=builder /app/extracted/dependencies/ ./
COPY --from=builder /app/extracted/spring-boot-loader/ ./
COPY --from=builder /app/extracted/snapshot-dependencies/ ./
COPY --from=builder /app/extracted/application/ ./

# The image carries the SPA, so it serves it rather than proxying a dev server.
ENV TRACKLY_SERVE_SPA=true

EXPOSE 8080

ENTRYPOINT ["dumb-init", "java", "-jar", "app.jar"]
