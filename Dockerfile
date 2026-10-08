# 앱 이미지: 빌드 단계(JDK 21 + Gradle Wrapper) → 실행 단계(JRE 21, root 아님).
# 비밀값은 이미지에 넣지 않고 실행할 때 환경 변수로만 준다(헌법 IV).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon -q dependencies > /dev/null || true
COPY src src
RUN ./gradlew --no-daemon -q bootJar -x test && cp build/libs/*-SNAPSHOT.jar /app.jar

FROM eclipse-temurin:21-jre
RUN groupadd --system blog && useradd --system --gid blog --home /app blog
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
USER blog
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
