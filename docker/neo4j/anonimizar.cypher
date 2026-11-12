// ============================================================================
// Anonimiza uma cópia do banco do OpenPMO para virar o seed local dos devs.
// Mantém a estrutura (escritórios, planos, modelos, pacotes de trabalho) e troca
// todo dado pessoal por dados fictícios e determinísticos.
// Executado por docker/neo4j/gerar-seed.sh — nunca contra um banco de verdade.
// ============================================================================

// 1. Pessoas: numeradas na ordem do id interno (Pessoa 01, 02, ...).
MATCH (p:Person)
WITH p ORDER BY id(p)
WITH collect(p) AS pessoas
UNWIND range(0, size(pessoas) - 1) AS i
WITH pessoas[i] AS p, right('0' + toString(i + 1), 2) AS n
SET p.name = 'Pessoa ' + n,
    p.fullName = 'Pessoa ' + n + ' Teste',
    p.contactEmail = 'pessoa' + n + '@openpmo.dev',
    p.idOffice = null, p.idPlan = null, p.idWorkpack = null;

// 2. O primeiro administrador vira o usuário admin@openpmo.dev.
MATCH (p:Person {administrator: true})
WITH p ORDER BY id(p) LIMIT 1
SET p.name = 'Admin', p.fullName = 'Administrador OpenPMO', p.contactEmail = 'admin@openpmo.dev';

// 3. Vínculo com o Keycloak: chave (sub) fixa e e-mail iguais aos usuários do realm local.
MATCH (p:Person)-[a:IS_AUTHENTICATED_BY]->(:AuthService)
WITH p, a, toInteger(replace(p.name, 'Pessoa ', '')) AS n
SET a.email = p.contactEmail,
    a.name = 'Keycloak',
    a.key = CASE WHEN p.contactEmail = 'admin@openpmo.dev'
                 THEN '00000000-0000-4000-8000-000000000000'
                 ELSE '00000000-0000-4000-8000-' + right('000000000000' + toString(n), 12) END;

// 4. Agenda de contatos e organizações: sem endereço/telefone reais.
MATCH (p:Person)-[c:IS_IN_CONTACT_BOOK_OF]->()
SET c.email = p.contactEmail, c.address = null, c.phoneNumber = null;
MATCH (o:Organization)
SET o.contactEmail = null, o.address = null, o.phoneNumber = null, o.website = null;

// 5. Textos livres que podem citar pessoas.
MATCH (j:JournalEntry) WHERE j.description IS NOT NULL SET j.description = 'Registro de exemplo';
MATCH (j:JournalEntry) WHERE j.reason IS NOT NULL SET j.reason = 'Motivo de exemplo';
MATCH (b:Baseline) SET b.description = 'Linha de base de exemplo', b.message = 'Proposta de exemplo', b.cancelation = null;

// 6. Arquivos anexados não existem no ambiente local.
MATCH (f:File) DETACH DELETE f;

// 7. Só o serviço de autenticação usado pela API (app.login.server.idsvr.name = Keycloak).
MATCH (a:AuthService) WHERE a.server <> 'Keycloak' DETACH DELETE a;

// 8. Qualquer outro e-mail em texto livre (propriedades e campos de valor) vira contato@openpmo.dev.
CALL apoc.periodic.iterate(
  "MATCH (n) RETURN n",
  "UNWIND keys(n) AS k
   WITH n, k WHERE apoc.meta.type(n[k]) = 'STRING' AND n[k] =~ '(?s).*[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+[.][A-Za-z]{2,}.*'
     AND NOT n[k] =~ '(?s)^[a-z0-9.]+@openpmo[.]dev$'
   CALL apoc.create.setProperty(n, k, apoc.text.regreplace(n[k], '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+[.][A-Za-z]{2,}', 'contato@openpmo.dev')) YIELD node
   RETURN count(*)", {batchSize: 500});
CALL apoc.periodic.iterate(
  "MATCH ()-[r]->() RETURN r",
  "UNWIND keys(r) AS k
   WITH r, k WHERE apoc.meta.type(r[k]) = 'STRING' AND r[k] =~ '(?s).*[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+[.][A-Za-z]{2,}.*'
     AND NOT r[k] =~ '(?s)^[a-z0-9.]+@openpmo[.]dev$'
   CALL apoc.create.setRelProperty(r, k, apoc.text.regreplace(r[k], '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+[.][A-Za-z]{2,}', 'contato@openpmo.dev')) YIELD rel
   RETURN count(*)", {batchSize: 500});

// 9. Contatos de organização cadastrada no escritório: sem telefone/endereço reais.
MATCH ()-[r:IS_REGISTERED_IN]->() SET r.address = null, r.phoneNumber = null, r.website = null;
