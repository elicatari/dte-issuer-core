# Echo de demo de dte.issued. Java 21, sin Spring. No es un segundo bounded context.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /src/target/dte-issued-echo.jar app.jar
USER nobody
ENTRYPOINT ["java", "-jar", "app.jar"]