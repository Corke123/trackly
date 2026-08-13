# syntax=docker/dockerfile:1
#
# Built from the repository root, not from gateway-service/: the image bundles the SPA so the
# browser talks to a single origin (ADR 0006).
#   docker build -f gateway-service/Dockerfile .

FROM node:24-alpine@sha256:d32cdf619f63fe0471182d08996dd516c6275bb5fd31ae06e55a570bd9e1ad43 AS frontend
WORKDIR /app

COPY trackly-client/package.json trackly-client/package-lock.json ./
RUN npm ci

COPY trackly-client/ ./
RUN npm run build

FROM eclipse-temurin:25-jdk-alpine@sha256:5ecfde8e5ecde5954ea3721155b345ef56c1d579b940c761318ad4c05959a151 AS builder
WORKDIR /app

RUN apk add --no-cache unzip

COPY gateway-service/.mvn/ .mvn/
COPY gateway-service/mvnw gateway-service/pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

ARG GIT_SHA=unknown
ARG BUILD_NUMBER=0
ARG BUILD_URL=local

COPY gateway-service/src ./src
# Spring serves classpath:/static/**, so the built SPA becomes part of the jar.
COPY --from=frontend /app/dist/trackly-client/browser/ ./src/main/resources/static/

RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q clean package -DskipTests \
      -Dgit.commit.id="$GIT_SHA" \
      -Dci.build.number="$BUILD_NUMBER" \
      -Dci.build.url="$BUILD_URL"

RUN java -Djarmode=tools -jar target/*.jar extract --layers --destination extracted && \
    mv extracted/application/*.jar extracted/application/app.jar

FROM eclipse-temurin:25-jre-alpine@sha256:28db6fdf60e38945e43d840c0333aeaec66c15943070104f7586fd3c9d1665b0 AS runtime
WORKDIR /app

RUN apk --no-cache upgrade && \
    apk add --no-cache dumb-init=1.2.5-r3 && \
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
