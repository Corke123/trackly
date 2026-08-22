# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jre-alpine AS extractor
WORKDIR /app

COPY target/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app

RUN apk --no-cache upgrade && \
    apk add --no-cache dumb-init=1.2.5-r4 && \
    addgroup -S appuser && \
    adduser -S -s /bin/false -G appuser appuser && \
    chown -R appuser:appuser /app

USER appuser

COPY --from=extractor /app/extracted/dependencies/ ./
COPY --from=extractor /app/extracted/spring-boot-loader/ ./
COPY --from=extractor /app/extracted/snapshot-dependencies/ ./
COPY --from=extractor /app/extracted/application/ ./

EXPOSE 8082

ENTRYPOINT ["dumb-init", "java", "-jar", "app.jar"]
