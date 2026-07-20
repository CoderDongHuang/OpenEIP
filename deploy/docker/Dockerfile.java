FROM eclipse-temurin:21-jdk-alpine@sha256:1ff763083f2993d57d0bf374ab10bb3e2cb873af6c13a04458ebbd3e0337dc76 AS builder

ARG GRADLE_DISTRIBUTION_URL=https://services.gradle.org/distributions/gradle-8.12.1-bin.zip
ARG MAVEN_REPOSITORY_URL=
WORKDIR /workspace
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/
COPY config/ config/
COPY platform-common/ platform-common/
COPY platform-auth/ platform-auth/
RUN sed -i "s|^distributionUrl=.*|distributionUrl=${GRADLE_DISTRIBUTION_URL}|" gradle/wrapper/gradle-wrapper.properties \
    && chmod +x gradlew \
    && if [ -n "$MAVEN_REPOSITORY_URL" ]; then \
         ./gradlew --no-daemon -PmavenRepositoryUrl="$MAVEN_REPOSITORY_URL" :platform-auth:bootJar; \
       else \
         ./gradlew --no-daemon :platform-auth:bootJar; \
       fi

FROM eclipse-temurin:21-jre-alpine@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c

RUN apk add --no-cache wget \
    && addgroup -S openeip \
    && adduser -S openeip -G openeip
WORKDIR /app
COPY --from=builder /workspace/platform-auth/build/libs/*.jar app.jar
USER openeip
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
