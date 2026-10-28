# ---------- Estágio 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache de dependências: copia só o pom primeiro.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

# Copia o código e empacota (pula testes no build da imagem; a CI roda os testes).
COPY src ./src
RUN mvn -q -B -DskipTests clean package

# ---------- Estágio 2: runtime ----------
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Usuário não-root.
RUN addgroup -S app && adduser -S app -G app

# Copia apenas o jar final (imagem enxuta).
COPY --from=build /app/target/activation-service-*.jar app.jar

# Ajuste de memória da JVM ao contêiner: usa percentual da RAM disponível no cgroup.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"

USER app
EXPOSE 8080

# Health check usando o endpoint de readiness do Actuator.
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/readiness | grep -q UP || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
