# syntax=docker/dockerfile:1
# Contexto de build = raiz do repositorio (mesmo padrao dos outros servicos).
# docker compose build banco-tranquilo-api

# ---------- Etapa 1: build com Maven ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
COPY mercado-commons/pom.xml mercado-commons/
COPY banco-tranquilo-api/pom.xml banco-tranquilo-api/
COPY pagamento-integracao/pom.xml pagamento-integracao/
COPY mercado-express/pom.xml mercado-express/
COPY banco-tranquilo-api/src banco-tranquilo-api/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl banco-tranquilo-api -am package -DskipTests

# ---------- Etapa 2: so o runtime ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /build/banco-tranquilo-api/target/banco-tranquilo-api-0.0.1-SNAPSHOT.jar app.jar
USER app

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
EXPOSE 18082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
