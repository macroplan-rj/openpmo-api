// Falha (retorna linhas) se sobrar e-mail fora de @openpmo.dev em qualquer propriedade de nó ou relacionamento.
CALL { MATCH (n) UNWIND keys(n) AS k WITH n[k] AS v WHERE apoc.meta.type(v) = "STRING" RETURN v
       UNION ALL MATCH ()-[r]->() UNWIND keys(r) AS k WITH r[k] AS v WHERE apoc.meta.type(v) = "STRING" RETURN v }
WITH v WHERE v =~ '(?s).*[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}.*' AND NOT v =~ '(?s)^[a-z0-9.]+@openpmo\\.dev$'
RETURN v LIMIT 20;
