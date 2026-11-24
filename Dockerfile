# syntax=docker/dockerfile:1
# Shared multi-stage build for every executable module. Select with --build-arg MODULE=<module>.
FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /workspace
COPY pom.xml ./
COPY chat-events/pom.xml chat-events/
COPY discovery-server/pom.xml discovery-server/
COPY gateway/pom.xml gateway/
COPY auth-service/pom.xml auth-service/
COPY chat-service/pom.xml chat-service/
COPY history-service/pom.xml history-service/
COPY presence-service/pom.xml presence-service/
COPY chat-events/src chat-events/src
COPY discovery-server/src discovery-server/src
COPY gateway/src gateway/src
COPY auth-service/src auth-service/src
COPY chat-service/src chat-service/src
COPY history-service/src history-service/src
COPY presence-service/src presence-service/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests -pl ${MODULE} -am package

FROM eclipse-temurin:21-jre
ARG MODULE
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/${MODULE}/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
