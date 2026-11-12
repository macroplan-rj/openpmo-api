#!/usr/bin/env bash
# Gera docker/neo4j/seed.dump (seed dos devs) a partir de um dump do OpenPMO, anonimizando os dados.
#
#   docker/neo4j/gerar-seed.sh /caminho/para/neo4j.dump
#
# O dump de origem NUNCA é versionado. Só o resultado anonimizado (seed.dump) entra no repositório,
# e só se docker/neo4j/verificar-anonimizacao.cypher não encontrar nenhum e-mail real.
set -euo pipefail

SRC="${1:?informe o caminho do dump de origem}"
DIR="$(cd "$(dirname "$0")" && pwd)"
IMG=neo4j:4.4.19
VOL=openpmo-seed-gen-$$
CT=openpmo-seed-gen-$$
PASS=seedgen
WORK="$(mktemp -d)"
trap 'docker rm -f "$CT" >/dev/null 2>&1 || true; docker volume rm "$VOL" >/dev/null 2>&1 || true; rm -rf "$WORK"' EXIT

cp "$SRC" "$WORK/origem.dump"
cs() { docker exec -i "$CT" cypher-shell -u neo4j -p "$PASS" --format plain "$@"; }

echo ">>> Carregando dump de origem"
docker run --rm -v "$WORK":/in:ro -v "$VOL":/data "$IMG" \
  neo4j-admin load --from=/in/origem.dump --database=neo4j --force >/dev/null

echo ">>> Subindo Neo4j temporário"
docker run -d --name "$CT" -e NEO4J_AUTH="neo4j/$PASS" \
  -e NEO4JLABS_PLUGINS='["apoc"]' -e NEO4J_dbms_security_procedures_unrestricted='apoc.*' \
  -v "$VOL":/data "$IMG" >/dev/null
until cs "RETURN apoc.version()" >/dev/null 2>&1; do sleep 3; done

echo ">>> Anonimizando"
cs < "$DIR/anonimizar.cypher" >/dev/null

echo ">>> Verificando"
SOBRAS="$(cs < "$DIR/verificar-anonimizacao.cypher" | tail -n +2)"
if [ -n "$SOBRAS" ]; then echo "ERRO: ainda há e-mails reais no seed:"; echo "$SOBRAS"; exit 1; fi
cs "MATCH (n) WITH count(n) AS nos MATCH ()-[r]->() RETURN nos, count(r) AS rels" | tail -1 | sed 's/^/    nós, relacionamentos: /'
cs "MATCH (p:Person)-[a:IS_AUTHENTICATED_BY]->() RETURN p.name, a.email, a.key ORDER BY a.key" | tail -n +2 > "$WORK/usuarios.csv"
cp "$WORK/usuarios.csv" "$DIR/../keycloak/usuarios-seed.csv"

echo ">>> Gerando dump anonimizado"
docker stop "$CT" >/dev/null
docker run --rm -v "$VOL":/data -v "$WORK":/out "$IMG" \
  neo4j-admin dump --database=neo4j --to=/out/seed.dump >/dev/null
cp "$WORK/seed.dump" "$DIR/seed.dump"
echo "OK: $DIR/seed.dump ($(du -h "$DIR/seed.dump" | cut -f1)); usuários em docker/keycloak/usuarios-seed.csv"
