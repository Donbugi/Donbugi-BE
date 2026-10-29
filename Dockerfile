FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src

RUN chmod +x ./gradlew && ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar /app/finedu.jar

# 8080: API, 8081: actuator(관리 포트, 외부 미노출)
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "/app/finedu.jar"]
