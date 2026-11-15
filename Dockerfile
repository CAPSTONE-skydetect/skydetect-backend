# SkyDetect 백엔드 (Spring Boot 4 / Java 21)

# 1) 빌드: gradle wrapper 로 실행 가능한 jar 를 만든다
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성 레이어를 먼저 받아 캐시한다. 소스만 바뀌면 이 단계는 재사용된다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test \
    && cp build/libs/*-SNAPSHOT.jar app.jar

# 2) 실행: JRE 만 담는다
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
