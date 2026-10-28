# Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# Runtime (JRE mínima, usuário sem privilégios)
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S tekcondo && adduser -S tekcondo -G tekcondo
WORKDIR /app
COPY --from=build /src/target/tekcondo-backend-*.jar app.jar
RUN mkdir -p /data/storage && chown tekcondo:tekcondo /data/storage
USER tekcondo
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
