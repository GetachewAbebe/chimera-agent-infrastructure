FROM maven:3.9.10-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests clean package

FROM eclipse-temurin:21-jre-jammy
ENV APP_HOME=/app
ENV PORT=8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"
WORKDIR ${APP_HOME}
RUN useradd --system --uid 10001 --create-home chimera
COPY --from=build /workspace/target/chimera-agent-infrastructure-0.1.0-SNAPSHOT.jar ${APP_HOME}/chimera.jar
RUN chown -R chimera:chimera ${APP_HOME}
USER chimera
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/chimera.jar"]
