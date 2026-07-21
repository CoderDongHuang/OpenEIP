FROM gradle:8.14.3-jdk21 AS build

WORKDIR /workspace
COPY settings.gradle.kts build.gradle.kts ./
COPY java-producer java-producer
RUN --mount=type=cache,target=/home/gradle/.gradle gradle --no-daemon spotlessCheck installDist

FROM eclipse-temurin:21.0.7_6-jre-noble
WORKDIR /app
COPY --from=build /workspace/build/install/spike-002-kafka-eventing/ ./
RUN mkdir /results && chown 10001:10001 /results
USER 10001:10001
CMD ["bin/spike-002-kafka-eventing"]
