# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-25-alpine AS builder
WORKDIR /app

COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

ARG GIT_SHA=unknown
ARG BUILD_NUMBER=0
ARG BUILD_URL=local

COPY src ./src
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

EXPOSE 9000

ENTRYPOINT ["dumb-init", "java", "-jar", "app.jar"]
