// Indicadores do projeto e das entregas dele, com as metas por periodo.
MATCH (w:Project) WHERE id(w) = $projeto
MATCH (i:Indicator)-[:RELATED_TO]->(x)
WHERE x = w OR (x)-[:IS_IN*1..3]->(w)
WITH DISTINCT i
OPTIONAL MATCH (g:PeriodGoal)-[:IS_DEFINED_FOR]->(i)
RETURN id(i) AS id, i.name AS nome, i.source AS fonte, i.description AS descricao, i.measure AS unidade,
       i.periodicity AS periodicidade, i.startDate AS inicio, i.endDate AS fim,
       collect(CASE WHEN g IS NULL THEN NULL ELSE {periodo: g.period, previsto: toFloat(g.expectedValue),
         realizado: toFloat(g.achievedValue), ultimaAtualizacao: g.lastUpdate} END) AS periodos
