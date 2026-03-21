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

# Bake SQLite DB into image (place cardsense.db in project root before building)
COPY data/cardsense.db /app/data/cardsense.db

ENV CARDSENSE_DB_PATH=/app/data/cardsense.db
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
