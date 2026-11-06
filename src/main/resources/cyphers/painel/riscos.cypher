// Riscos do projeto e das entregas dele, com os planos de resposta.
MATCH (w:Project) WHERE id(w) = $projeto
MATCH (r:Risk)-[:IS_FORSEEN_ON]->(x)
WHERE x = w OR (x)-[:IS_IN*1..3]->(w)
WITH DISTINCT r
OPTIONAL MATCH (rr:RiskResponse)-[:MITIGATES]->(r)
OPTIONAL MATCH (resp:Person)-[:IS_RESPONSIBLE_FOR]->(rr)
WITH r, rr, collect(DISTINCT resp.name) AS responsaveis
RETURN id(r) AS id, r.name AS nome, r.description AS descricao, r.importance AS importancia, r.nature AS natureza,
       r.status AS status, left(toString(coalesce(r.likelyToHappenTo, r.likelyToHappenFrom)), 10) AS dataProvavel,
       collect(CASE WHEN rr IS NULL THEN NULL ELSE {nome: rr.name, estrategia: rr.strategy, situacao: rr.status,
         quando: rr.when, responsaveis: responsaveis} END) AS planos
