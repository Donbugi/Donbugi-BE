FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src

RUN chmod +x ./gradlew && ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar /app/finedu.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/finedu.jar"]
