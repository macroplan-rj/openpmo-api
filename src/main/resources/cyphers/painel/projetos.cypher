// Projetos do painel: linha de base aprovada e ativa, nao excluidos nem cancelados.
MATCH (w:Project)-[:IS_BASELINED_BY]->(:Baseline {active: true, status: 'APPROVED'})
WHERE coalesce(w.deleted, false) = false AND coalesce(w.canceled, false) = false
WITH DISTINCT w
OPTIONAL MATCH (w)-[:IS_IN*1..4]->(org:Organizer)
WITH w, head(collect(DISTINCT org)) AS org
OPTIONAL MATCH (w)-[bt:BELONGS_TO]->(pl:Plan)
WITH w, org, head(collect(CASE WHEN coalesce(bt.linked, false) = false THEN pl END)) AS pl
OPTIONAL MATCH (w)<-[:FEATURES]-(:Property)-[:GROUPS*0..2]->(p:Property)-[:IS_DRIVEN_BY]->(pm:PropertyModel)
WHERE NOT (p)-[:GROUPS]->()
OPTIONAL MATCH (p)-[:VALUES]->(sel)
WITH w, org, pl, collect({
  n: toLower(trim(replace(pm.name, '​', ''))),
  v: coalesce(p.label, toString(p.value), toString(p.date)),
  bruto: toString(p.value),
  sel: sel.name, selId: id(sel)
}) AS props
OPTIONAL MATCH (a:Actor)-[r:IS_STAKEHOLDER_IN]->(w)
WHERE coalesce(r.active, true)
WITH w, org, pl, props, collect({nome: a.name, id: id(a), organizacao: 'Organization' IN labels(a), papel: toLower(trim(r.role))}) AS partes
RETURN id(w) AS id, w.name AS nome, id(org) AS areaId, org.name AS area, id(pl) AS planoId, pl.name AS plano, props, partes
