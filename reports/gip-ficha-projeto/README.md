# Ficha de Projeto GIP — relatório piloto JasperReports (SA-528)

Relatório piloto que valida a funcionalidade de relatórios do OpenPMO no ambiente da Paraíba.
Implementa o `Modelo Jasper v1.xlsx` anexado à SA-528 — 5 seções, uma ficha por página.

Vinculado ao **PlanModel 682 "Ficha de projetos GIP"**. A escolha veio dos campos do modelo:
`Público-alvo` e `Responsável` só existem nesse PlanModel (o 39 "Ficha base de projetos" não os tem).

## Arquivos

| Arquivo | Papel | Seção do xlsx |
|---|---|---|
| `ficha_projeto_gip.jrxml` | **principal** (main) | — |
| `sub_cabecalho.jrxml` | subreport | Parâmetros (Cabeçalho) |
| `sub_painel.jrxml` | subreport | Painel-Resumo |
| `sub_indicador.jrxml` | subreport | Indicador |
| `sub_entregas.jrxml` | subreport | Entregas |
| `sub_marcos.jrxml` | subreport | Marcos Críticos |

`exemplo-saida.pdf` é uma saída real gerada contra o Neo4j do dev (projeto 514, que tem
entregas/marcos/indicadores cadastrados) — serve de referência visual.

## Contrato com o motor de relatórios

Três amarrações do `GenerateReportComponent` que os arquivos dependem:

1. **`$P{scope}`** — o backend injeta a lista de ids de workpack que o usuário pode acessar, como
   string separada por vírgula. Precisa ser consumida com **`$P!{scope}`** (substituição textual):
   `WHERE id(w) IN [$P!{scope}]`. Com `$P{}` o Jasper transforma em parâmetro posicional `?` e a
   lista não expande.

2. **Nome do subreport = nome do arquivo sem extensão.** `subReportsAsParams` registra cada
   `.jasper` não-principal como parâmetro com esse nome. Renomear `sub_entregas.jrxml` exige
   renomear `$P{sub_entregas}` no principal.

3. **Exatamente um arquivo `main`.** `CompileReportComponent.verifyQuantityMain` rejeita a
   compilação se a contagem de arquivos marcados como principal for diferente de 1.

Cada subreport recebe `idProjeto` (o `$F{id}` da linha corrente) e reusa a conexão via
`$P{REPORT_CONNECTION}`.

## Importação (tela `/report-models`, requer perfil administrador)

1. Criar o modelo de relatório apontando para o PlanModel **Ficha de projetos GIP**.
2. Subir os 6 `.jrxml`. Marcar **`ficha_projeto_gip.jrxml` como principal**; os outros 5 não.
3. Acionar a compilação (`PATCH /report-model/compile/{id}`) — gera os `.jasper` em
   `app.reportPath` e marca o modelo como ativo.
4. Gerar em `/reports`, escolhendo o plano GIP como escopo.

Os `.jasper` são artefatos de build: o app os gera na compilação, por isso não são versionados aqui.

## Armadilhas do Cypher via JDBC bolt (encontradas na validação)

Custaram tempo real e não são óbvias — o driver é `neo4j-jdbc-bolt 4.0.1` sobre Neo4j 4.4.19.

- **Não concatenar listas vindas de `collect()`.** `collect(a) + collect(b)` quebra a inferência de
  tipo do planner: `Type mismatch: expected Float, Integer, String or List<T> but was Boolean`.
  Solução: um único `OPTIONAL MATCH` com `WHERE o:Risk OR o:Issue` e um `collect` só.
- **Comparação de data exige normalização.** `m.date` mistura `Date` e `DateTime` no mesmo campo;
  `m.date < date()` falha silenciosamente (marco vencido aparecia "No prazo"). Usar
  `date(left(toString(coalesce(m.newDate, m.date)), 10))`.
- **`toString()` de float sai em notação científica** — `4.7E8` em vez de `470000000`. Nas tabelas
  Campo/Valor, retornar uma coluna `valorNum` (Double) separada e deixar o Jasper formatar com
  `pattern="¤ #,##0.00"`, alternando os dois `textField` por `printWhenExpression`.
- **`stretchType="RelativeToTallestObject"` em banda de posicionamento absoluto** estica toda célula
  até a altura do maior elemento da banda. Por isso Cabeçalho e Painel-Resumo são subreports de
  2 colunas com banda de detalhe (que cresce sozinha), não campos posicionados à mão.
- **`isBlankWhenNull="true"`** em todo `textField` — sem isso o PDF imprime a string `null`.
- Sem fontes customizadas de propósito: fonte customizada exigiria um font-extension jar na imagem.

## Mapeamento dos campos

### Parâmetros (Cabeçalho)

| Campo do xlsx | Origem no grafo |
|---|---|
| ID | `id(w)` — o PlanModel 682 não tem propriedade `ID` |
| Nome do Projeto | `w.name` |
| Plano | `Plan.name` via `BELONGS_TO` |
| Programa/Carteira Estratégica | propriedade `Programa do PPA` |
| **Área de Resultado** | ❌ não existe no modelo GIP |
| Gerente/Responsável | propriedade `Responsável` |
| Partes Interessadas | `Person` via `IS_STAKEHOLDER_IN` |
| Público-alvo | propriedade `Público-alvo` |
| Justificativa | propriedade `Justificativa` |
| Resultados Esperados | propriedade `Resultados esperados` |
| Data de Início / Término | propriedades `Data de início` / `Data de término` |
| Valor estimado do projeto | `Valor de investimento (CAPEX)` **+** `Valor de custeio (OPEX)` — o modelo GIP separa, o xlsx pede um valor único |
| Unidade Gestora | propriedade `Unidade gestora` |
| Demais unidades envolvidas | propriedade `Demais unidades` |
| Linha de base | `Baseline` via `IS_BASELINED_BY` / `IS_PROPOSED_BY` |

### Painel-Resumo

| Campo do xlsx | Origem no grafo |
|---|---|
| Data de Referência | `date()` na geração |
| Custo total previsto | CAPEX + OPEX |
| **Custo realizado** | ❌ exige `CostAccount` vinculada — inexistente no ambiente |
| Execução física planejada / realizada | `sum(Step.plannedWork)` / `sum(Step.actualWork)` via `Schedule -[:FEATURES]-> Deliverable` |
| % cumprimento físico | realizada ÷ planejada |
| **Execução financeira (plan./real./%)** | ❌ mesma dependência de `CostAccount` |
| N° de Entregas cadastradas | `count(Deliverable)` via `IS_IN*1..3` |
| Status das Entregas | propriedade `Situação` das entregas, agregada |
| Riscos | `Risk.name`, agregado |

### Indicador / Entregas / Marcos Críticos

- **Indicador**: `Indicator {name, source, measure}` via `RELATED_TO`; metas em
  `PeriodGoal {expectedValue, achievedValue, lastUpdate}` via `IS_DEFINED_FOR`.
- **Entregas**: `Deliverable` + propriedades `Tipo`, `Situação`, `Custo Operacional Anual`;
  datas e % de execução do `Schedule`/`Step`.
- **Marcos**: `Milestone {name, date, newDate, completed, canceled}`; status derivado
  (Cancelado / Concluído / Atrasado / No prazo); Ocorrência a partir de `Risk` e `Issue` do pai.

## Lacunas — precisam de decisão de negócio (Luiz Roque)

1. **Área de Resultado** não existe como propriedade no PlanModel GIP. Criar a propriedade ou
   remover do modelo do relatório.
2. **Custo realizado e execução financeira** dependem de Conta de Custo vinculada às entregas.
   Nenhum projeto do ambiente tem lançamento. Enquanto isso as três linhas saem com nota explicativa.
3. **Meta Realizada** usa `PeriodGoal.achievedValue`, que existe só em parte dos nós — confirmar se
   é o campo correto ou se há outra origem.
4. **Valor estimado**: somar CAPEX + OPEX é a interpretação adotada. Confirmar.

## Estado da validação no ambiente dev

Validado localmente ponta a ponta contra o Neo4j do dev (via port-forward), replicando exatamente o
caminho do `GenerateReportComponent` — mesmo JasperReports 6.17.0, mesmo driver JDBC:

- ✅ os 6 `.jrxml` compilam
- ✅ preenchimento e export PDF funcionam; subreports recebem a conexão e o `idProjeto`
- ✅ com dados reais (projeto 514) as 5 seções renderizam corretamente — ver `exemplo-saida.pdf`

⚠️ **Os 3 projetos do plano GIP não têm entrega, marco, indicador, risco, cronograma nem baseline.**
As seções 3, 4 e 5 saem vazias para eles. Para fechar o critério "dados apresentados conforme
especificação" é preciso cadastrar, pela tela, no plano GIP: ao menos 1 entrega (com cronograma),
1 marco e 1 indicador com meta.
