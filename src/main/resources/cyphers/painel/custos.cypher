// Cronograma por mes das entregas: fisico e custo vigentes, e o estimado da linha de base aprovada ativa.
MATCH (w:Project) WHERE id(w) IN $projetos
MATCH (w)<-[:IS_IN*1..3]-(d:Deliverable)
WHERE coalesce(d.deleted, false) = false AND coalesce(d.category, '') <> 'SNAPSHOT'
WITH DISTINCT d
MATCH (sc:Schedule)-[:FEATURES]->(d)
WHERE coalesce(sc.category, '') <> 'SNAPSHOT'
MATCH (st:Step)-[:COMPOSES]->(sc)
WITH d, st, date(left(toString(sc.start), 10)) + duration({months: st.periodFromStart}) AS mes
OPTIONAL MATCH (sb:Step)-[:IS_SNAPSHOT_OF]->(st)
WHERE (sb)-[:COMPOSES]->(:Baseline {active: true, status: 'APPROVED'})
WITH d, st, mes, head(collect(sb)) AS sb
OPTIONAL MATCH (st)-[c:CONSUMES]->(ca:CostAccount)
OPTIONAL MATCH (uo:UnidadeOrcamentaria)-[:CONTROLS*1..2]->(ca)
OPTIONAL MATCH (sb)-[cs:CONSUMES]->(:CostAccount)-[:IS_SNAPSHOT_OF]->(ca)
WITH d, st, mes, sb, ca, head(collect(uo)) AS uo, c, head(collect(cs)) AS cs
WITH d, st, mes, sb, collect(CASE WHEN ca IS NULL THEN NULL ELSE {
  ca: id(ca), uoCodigo: uo.code, uo: uo.name,
  planejado: toFloat(coalesce(c.plannedCost, 0)), realizado: toFloat(coalesce(c.actualCost, 0)),
  estimado: toFloat(coalesce(cs.plannedCost, 0))
} END) AS consumos
RETURN id(d) AS entregaId, mes.year * 100 + mes.month AS anomes,
       toFloat(coalesce(st.plannedWork, 0)) AS fisicoPlanejado, toFloat(coalesce(st.actualWork, 0)) AS fisicoRealizado,
       toFloat(coalesce(sb.plannedWork, 0)) AS fisicoEstimado, sb IS NOT NULL AS temLinhaBase, consumos
