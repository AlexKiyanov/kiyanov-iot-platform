FROM gradle:9.0.0-jdk24 AS builder
WORKDIR /app
COPY --chown=gradle:gradle . .
RUN gradle clean build -x test --no-daemon

FROM openjdk:24-jdk
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]