# Dockerfile do OpenPMO API (Spring Boot 2.2 / Java 11) para o pipeline da CODATA
# (componente cicd/build). A imagem é publicada em
# container-registry.codata.pb.gov.br/library/seplag/sipgr/govpb-openpmo-backend.
#
# O build.gradle lê `app.parser.repository` de src/main/resources/application.properties
# EM TEMPO DE BUILD (arquivo gitignored) e adiciona o jar do parser de identidade
# (Keycloak/OIDC, publicado via JitPack) como dependência — por isso o arquivo é
# gerado aqui antes do bootJar.
#
# As demais configurações (Neo4j, OAuth2, JWT, etc.) NÃO ficam na imagem: vêm em
# runtime via SPRING_APPLICATION_JSON (ConfigMap) e variáveis de ambiente (Secret),
# declaradas na branch `kubernetes` deste repositório.

# ---------- Build stage ----------
FROM eclipse-temurin:11-jdk AS builder

WORKDIR /app
COPY . .

RUN printf 'app.parser.repository=com.github.macroplan-rj:keycloak-identity-parser:1.0.0\n' \
    > src/main/resources/application.properties

# OBS: o OpenPMO usa Spring Security 5.2.x com Nimbus antiga, que quebra no OIDC
# discovery (issuer-uri) contra Keycloak 25. Os endpoints do Keycloak são
# configurados explicitamente no SPRING_APPLICATION_JSON, sem discovery.
RUN chmod +x gradlew && ./gradlew bootJar -x test

# ---------- Run stage ----------
FROM eclipse-temurin:11-jre

VOLUME /tmp
COPY --from=builder /app/build/libs/*.jar app.jar

RUN apt-get update && apt-get install -y --no-install-recommends \
      fontconfig fonts-dejavu wget && \
    rm -rf /var/lib/apt/lists/*

RUN mkdir -p /tmp/open-pmo/imagens /tmp/open-pmo/journal /tmp/open-pmo/report

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -Djava.security.egd=file:/dev/./urandom -Duser.region=BR -Duser.language=pt ${JAVA_OPTS} -jar /app.jar"]
