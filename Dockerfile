# syntax=docker/dockerfile:1

# ---- Estágio 1: compila o JAR (os testes rodam antes, na pipeline) ----
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# O cache do Maven (/root/.m2) é mantido entre builds, então as dependências não são baixadas de novo
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
    && cp target/execution-service-*.jar /app/app.jar

# Agente Java do New Relic. Se o download falhar, a imagem continua valida e roda sem agente.
RUN mkdir -p /app/newrelic \
    && (curl -fsSL -o /tmp/nr.zip https://download.newrelic.com/newrelic/java-agent/newrelic-agent/current/newrelic-java.zip \
        && cd /tmp && jar xf nr.zip && cp newrelic/newrelic.jar /app/newrelic/ ) || echo "Agente New Relic indisponivel no build; seguindo sem ele"

# ---- Estágio 2: imagem final, só com o JRE e sem root ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app/app.jar app.jar
COPY --from=build /app/newrelic /app/newrelic
USER app
EXPOSE 8083
# Limita a memória da JVM a uma fração do limite do contêiner
ENV PORT=8083 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError"
# O agente New Relic so e ligado quando existe NEW_RELIC_LICENSE_KEY (segredo opcional no Kubernetes)
ENTRYPOINT ["sh", "-c", "if [ -n \"$NEW_RELIC_LICENSE_KEY\" ] && [ -f /app/newrelic/newrelic.jar ]; then export JAVA_TOOL_OPTIONS=\"$JAVA_TOOL_OPTIONS -javaagent:/app/newrelic/newrelic.jar\"; fi; exec java -jar /app/app.jar"]
