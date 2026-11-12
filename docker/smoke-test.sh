#!/usr/bin/env bash
# Valida o ambiente local subido pelo docker compose. Uso: docker/smoke-test.sh
set -euo pipefail
WEB=http://localhost:4200
KC=http://localhost:8180/realms/openpmo
ok()   { printf '  \033[32mOK\033[0m   %s\n' "$1"; }
fail() { printf '  \033[31mFALHA\033[0m %s\n' "$1"; exit 1; }
json() { python3 -c "import sys,json; d=json.load(sys.stdin); print($1)"; }

echo "OpenPMO local — smoke test"

curl -sf "$WEB/health" >/dev/null && ok "web no ar ($WEB)" || fail "web não responde"
curl -sf "$WEB/openpmo/configuration" >/dev/null && ok "API no ar (via proxy do web)" || fail "API não responde"

NOS=$(curl -sf -u neo4j:openpmo -H 'Content-Type: application/json' http://localhost:7474/db/neo4j/tx/commit \
  -d '{"statements":[{"statement":"MATCH (n) RETURN count(n)"}]}' | json "d['results'][0]['data'][0]['row'][0]")
[ "$NOS" -gt 1000 ] && ok "Neo4j com o seed ($NOS nós)" || fail "Neo4j sem o seed ($NOS nós)"

TOKEN=$(curl -sf "$KC/protocol/openid-connect/token" -d grant_type=password -d client_id=openpmo \
  -d client_secret=openpmo-local-secret -d username=admin -d password=openpmo | json "d['access_token']")
[ -n "$TOKEN" ] && ok "Keycloak emitiu token para admin@openpmo.dev" || fail "Keycloak não emitiu token"

ACESSO=$(curl -sf -X POST "$WEB/openpmo/signin/portal-token" -H 'Content-Type: application/json' \
  -d "{\"accessToken\":\"$TOKEN\"}" | json "d['token']") || fail "API não reconheceu o usuário do Keycloak"
ok "API reconheceu o usuário (sessão OpenPMO emitida)"

ESCRITORIOS=$(curl -sf "$WEB/openpmo/offices" -H "Authorization: Bearer $ACESSO" | json "len(d['data'])")
[ "$ESCRITORIOS" -gt 0 ] && ok "escritórios visíveis para o admin ($ESCRITORIOS)" || fail "nenhum escritório visível"

LOC=$(curl -s -o /dev/null -w '%{redirect_url}' "$WEB/openpmo/oauth2/authorization/idsvr?front_callback_url=$WEB")
case "$LOC" in
  http://localhost:8180/realms/openpmo/*redirect_uri=http://localhost:4200/openpmo/login/oauth2/code/idsvr*) ok "botão Entrar redireciona para o Keycloak local";;
  *) fail "redirect do login inesperado: $LOC";;
esac

echo "Tudo certo. Acesse $WEB e entre com admin@openpmo.dev / openpmo."
