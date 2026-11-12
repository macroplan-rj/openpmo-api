#!/bin/bash
# Carrega o seed no volume do Neo4j só na primeira subida (sentinela em /data).
# Para recarregar do zero: docker compose down -v && docker compose up -d
set -e
SENTINELA=/data/.openpmo-seed
if [ -f "$SENTINELA" ]; then
  echo "Neo4j já inicializado com o seed. Nada a fazer."
  exit 0
fi
echo "Primeira subida: carregando /seed/seed.dump..."
neo4j-admin load --from=/seed/seed.dump --database=neo4j --force
sha256sum /seed/seed.dump | cut -d' ' -f1 > "$SENTINELA"
echo "Seed carregado."
