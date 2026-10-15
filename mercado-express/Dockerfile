# syntax=docker/dockerfile:1
# Contexto de build = raiz do repositorio (o servico depende do mercado-commons).
# docker compose build mercado-express

# ---------- Etapa 1: build com Maven (reactor: commons + servico) ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
COPY mercado-commons/pom.xml mercado-commons/
COPY banco-tranquilo-api/pom.xml banco-tranquilo-api/
COPY pagamento-integracao/pom.xml pagamento-integracao/
COPY mercado-express/pom.xml mercado-express/
COPY mercado-commons/src mercado-commons/src
COPY mercado-express/src mercado-express/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl mercado-express -am package -DskipTests

# ---------- Etapa 2: so o runtime ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /build/mercado-express/target/mercado-express-0.0.1-SNAPSHOT.jar app.jar
USER app

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
