# Build: dependencias primero para cachear. Runtime: capas, no-root, HEALTHCHECK.
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -q -DskipTests package \
    && mv target/dte-issuer-*.jar /src/application.jar \
    && java -Djarmode=tools -jar /src/application.jar extract --layers --destination /src/extracted \
    && mkdir -p /src/extracted/dependencies /src/extracted/spring-boot-loader \
        /src/extracted/snapshot-dependencies /src/extracted/application \
    && touch /src/extracted/spring-boot-loader/.keep /src/extracted/snapshot-dependencies/.keep

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system --gid 10001 dte \
    && useradd --system --uid 10001 --gid dte --no-create-home --shell /usr/sbin/nologin dte
COPY --from=build --chown=dte:dte /src/extracted/dependencies/ ./
COPY --from=build --chown=dte:dte /src/extracted/spring-boot-loader/ ./
COPY --from=build --chown=dte:dte /src/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=dte:dte /src/extracted/application/ ./
USER dte
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=12 \
    CMD bash -c "exec 3<>/dev/tcp/127.0.0.1/8080; printf 'GET /actuator/health/readiness HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n' >&3; grep -q UP <&3"
ENTRYPOINT ["java", "-jar", "application.jar"]