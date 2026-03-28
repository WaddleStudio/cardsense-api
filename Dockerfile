# ── Build stage ──
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

COPY pom.xml .
COPY src ./src

RUN apt-get update && apt-get install -y maven \
    && mvn -B package -DskipTests \
    && mv target/cardsense-api-*.jar target/app.jar

# ── Runtime stage ──
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /build/target/app.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod
ENV PORT=8080
EXPOSE ${PORT}

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT} -jar app.jar"]
