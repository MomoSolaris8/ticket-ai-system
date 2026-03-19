FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY ticket-service/pom.xml ticket-service/pom.xml
COPY orchestrator-service/pom.xml orchestrator-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
RUN mvn dependency:go-offline -q || true
COPY api-gateway/src api-gateway/src
COPY ticket-service/src ticket-service/src
COPY orchestrator-service/src orchestrator-service/src
COPY notification-service/src notification-service/src
RUN mvn package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/orchestrator-service/target/orchestrator-service-*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
