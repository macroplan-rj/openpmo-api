// Marcos criticos dos projetos informados, com tipo, data vigente, data da linha de base e entrega associada.
MATCH (w:Project) WHERE id(w) IN $projetos
MATCH (w)<-[:IS_IN*1..4]-(m:Milestone)
WHERE coalesce(m.deleted, false) = false AND coalesce(m.category, '') <> 'SNAPSHOT'
WITH DISTINCT w, m
OPTIONAL MATCH (m)-[:IS_IN]->(d:Deliverable)
OPTIONAL MATCH (m)<-[:FEATURES]-(:Property)-[:GROUPS*0..2]->(p:Property)-[:IS_DRIVEN_BY]->(pm:PropertyModel)
WHERE toLower(trim(replace(pm.name, '​', ''))) = 'tipo'
OPTIONAL MATCH (p)-[:VALUES]->(sel)
OPTIONAL MATCH (s:Milestone)-[:IS_SNAPSHOT_OF]->(m)
WHERE (s)-[:COMPOSES]->(:Baseline {active: true, status: 'APPROVED'})
WITH w, m, d, head(collect(coalesce(sel.name, p.label, toString(p.value)))) AS tipo, head(collect(s.date)) AS dataLinhaBase
RETURN id(w) AS projetoId, id(m) AS id, m.name AS nome, tipo, left(toString(m.date), 10) AS data,
       left(toString(dataLinhaBase), 10) AS dataLinhaBase, coalesce(m.completed, false) AS concluido,
       coalesce(m.canceled, false) AS cancelado, id(d) AS entregaId
