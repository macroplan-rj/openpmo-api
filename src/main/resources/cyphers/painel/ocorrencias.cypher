// Ocorrencias do projeto e das entregas dele.
MATCH (w:Project) WHERE id(w) = $projeto
MATCH (o:Issue)-[:IS_REPORTED_FOR]->(x)
WHERE x = w OR (x)-[:IS_IN*1..3]->(w)
WITH DISTINCT o
RETURN id(o) AS id, o.name AS nome, o.description AS descricao, o.importance AS importancia, o.nature AS natureza,
       o.status AS status
