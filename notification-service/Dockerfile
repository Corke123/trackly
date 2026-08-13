# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk-alpine@sha256:5ecfde8e5ecde5954ea3721155b345ef56c1d579b940c761318ad4c05959a151 AS builder
WORKDIR /app

RUN apk add --no-cache unzip

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

ARG GIT_SHA=unknown
ARG BUILD_NUMBER=0
ARG BUILD_URL=local

COPY src ./src
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

EXPOSE 8082

ENTRYPOINT ["dumb-init", "java", "-jar", "app.jar"]