# syntax=docker/dockerfile:1
# Contexto de build = raiz do repositorio (o servico depende do mercado-commons).
# docker compose build pagamento-integracao

# ---------- Etapa 1: build com Maven (reactor: commons + servico) ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
COPY mercado-commons/pom.xml mercado-commons/
COPY banco-tranquilo-api/pom.xml banco-tranquilo-api/
COPY pagamento-integracao/pom.xml pagamento-integracao/
COPY mercado-express/pom.xml mercado-express/
COPY mercado-commons/src mercado-commons/src
COPY pagamento-integracao/src pagamento-integracao/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl pagamento-integracao -am package -DskipTests

# ---------- Etapa 2: so o runtime ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /build/pagamento-integracao/target/pagamento-integracao-0.0.1-SNAPSHOT.jar app.jar
USER app

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
