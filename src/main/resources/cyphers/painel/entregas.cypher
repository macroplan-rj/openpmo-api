// Entregas dos projetos informados, com propriedades, cronograma vigente e gerentes.
MATCH (w:Project) WHERE id(w) IN $projetos
MATCH (w)<-[:IS_IN*1..3]-(d:Deliverable)
WHERE coalesce(d.deleted, false) = false AND coalesce(d.category, '') <> 'SNAPSHOT'
WITH DISTINCT w, d
OPTIONAL MATCH (d)<-[:FEATURES]-(:Property)-[:GROUPS*0..2]->(p:Property)-[:IS_DRIVEN_BY]->(pm:PropertyModel)
WHERE NOT (p)-[:GROUPS]->()
OPTIONAL MATCH (p)-[:VALUES]->(sel)
WITH w, d, collect({
  n: toLower(trim(replace(pm.name, '​', ''))),
  v: coalesce(p.label, toString(p.value), toString(p.date)),
  sel: sel.name, selId: id(sel), selTipo: sel.type
}) AS props
OPTIONAL MATCH (sc:Schedule)-[:FEATURES]->(d)
WHERE coalesce(sc.category, '') <> 'SNAPSHOT'
WITH w, d, props, head(collect(sc)) AS sc
OPTIONAL MATCH (a:Actor)-[r:IS_STAKEHOLDER_IN]->(d)
WHERE toLower(trim(r.role)) = 'gerente' AND coalesce(r.active, true)
RETURN id(w) AS projetoId, id(d) AS id, d.name AS nome, coalesce(d.completed, false) AS concluido,
       toString(d.endManagementDate) AS encerramento, toString(sc.start) AS inicio, toString(sc.end) AS termino,
       props, [x IN collect(DISTINCT a.name) WHERE x IS NOT NULL] AS gerentes
