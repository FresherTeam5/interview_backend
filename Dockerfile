# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
COPY database_docs/migrations ./database_docs/migrations
RUN --mount=type=cache,target=/root/.m2 \
    for attempt in 1 2 3; do \
        mvn -B -U -DskipTests -Dmaven.wagon.http.retryHandler.count=3 package && exit 0; \
        echo "Maven build attempt ${attempt} failed; retrying..."; \
    done; \
    exit 1

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

RUN groupadd --system spring \
    && useradd --system --gid spring spring

COPY --from=build --chown=spring:spring \
    /workspace/target/InterviewBackend-*.jar /app/app.jar

USER spring

ENV PORT=10000
ENV JAVA_TOOL_OPTIONS="-Xms64m -Xmx300m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC"

EXPOSE 10000

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
