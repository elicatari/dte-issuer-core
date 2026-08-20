FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -q -DskipTests package && mv target/dte-issuer-*.jar /src/app.jar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /src/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]