# API do Painel Estratégico (OpenPMO)

Contrato da API de dados que o OpenPMO expõe para o Painel Estratégico (SD #10795, #10796, #10797, #10798).
As telas do painel ficam fora do OpenPMO (SD #10801). Esta API é só de leitura.

Swagger (lista de rotas e parâmetros): `https://<host-do-openpmo>/openpmo/documentation/index.html`, grupo **painel-controller**.

## 1. Acesso

- Base: `https://<host-do-openpmo>/openpmo/painel`
- Autenticação por **token de serviço**, enviado no cabeçalho:

  ```
  X-Api-Key: <token>
  ```

- O token é só de leitura e vale apenas para `/painel/*`. Ele é fornecido pela equipe do OpenPMO, um por ambiente.
- Sem token ou com token inválido: `401` com `{ "erro": "invalid.token" }`.
- Guarde o token no servidor do painel; ele dá acesso à carteira inteira.

## 2. Regras gerais

- **Escopo:** entram somente projetos com **linha de base aprovada e ativa**, não excluídos nem cancelados. Projetos sem
  linha de base aprovada não aparecem em nenhum endpoint.
- **Posição:** todos os cálculos usam uma data de posição (`dataPosicao`); se não for informada, vale a data de hoje.
- **Horizonte financeiro** (`horizonte`):
  - `ATE_HOJE`: estimado e reprogramado somados até o mês da posição;
  - `TOTAL`: estimado e reprogramado do cronograma inteiro;
  - o **realizado** é sempre o acumulado até a posição.
- **Valores:** monetários em reais (número decimal, sem formatação); percentuais de 0 a 100; datas em `AAAA-MM-DD`.
- **Origem dos números:**
  - estimado = custo planejado na linha de base aprovada;
  - reprogramado = custo planejado vigente;
  - realizado = custo realizado informado no cronograma.
- **Entrega concluída:** situação "Concluída". A **data de conclusão** é a data de encerramento do gerenciamento da
  entrega; se não houver, é o último mês com realizado no cronograma.
- **Instituição responsável:** a propriedade de instituição/unidade gestora do workpack; se não houver, a organização
  com o papel "Instituição responsável" nas partes interessadas.
- **Local:** município registrado no campo "Local da Entrega" (`id` e `nome` do município no OpenPMO). O OpenPMO não
  guarda coordenadas nem código IBGE; o painel cruza pelo nome.
- **Erros:** `401` (token), `404` (projeto fora do escopo) e `500`; o corpo traz `{ "erro": "<chave>" }`.
- Valores que não estão nas listas oficiais (tipo, situação) aparecem com o próprio nome; campo vazio aparece como
  "Não informado".

## 3. Filtros

Valem para todos os endpoints de carteira. São cumulativos; listas separadas por vírgula.

| Parâmetro | Tipo | Exemplo |
|---|---|---|
| `area` | ids de área de resultado | `area=12,15` |
| `objetivo` | códigos de objetivo do PELP | `objetivo=OBJ03,OBJ17` |
| `projeto` | ids de projeto | `projeto=514` |
| `instituicao` | nomes da instituição responsável | `instituicao=SEPLAG,DOCAS` |
| `horizonte` | `ATE_HOJE` ou `TOTAL` | padrão `ATE_HOJE` |
| `dataPosicao` | data | padrão hoje |

## 4. Endpoints

### 4.1 `GET /painel/filtros`

Opções dos filtros, já restritas ao escopo.

```json
{
  "dataPosicao": "2026-11-06",
  "areas": [{ "id": 12, "nome": "Paraíba Inovadora" }],
  "objetivos": [{ "codigo": "OBJ15", "nome": "Elevar a capacidade de inovação..." }],
  "projetos": [{ "id": 514, "nome": "DIGITALIZA PB", "areaId": 12 }],
  "instituicoes": [{ "nome": "SEPLAG" }]
}
```

### 4.2 `GET /painel/carteira` (painel gerencial, SD #10795)

```json
{
  "dataPosicao": "2026-11-06",
  "projetosNaCarteira": {
    "totalProjetos": 4,
    "totalEntregas": 13,
    "porArea": [{ "areaId": 12, "area": "Paraíba Inovadora", "projetos": 2 }]
  },
  "entregas": { "total": 13, "concluidas": 3, "atrasadas": 2 },
  "marcosCriticos": { "total": 27, "concluidos": 18, "atrasados": 4 },
  "financeiro": { "estimado": 350000.00, "reprogramado": 350000.00, "realizado": 621544.48 },
  "composicaoEntregas": {
    "porTipo": [{ "tipo": "Obra", "quantidade": 5, "percentual": 38.46 }],
    "porSituacao": [{ "situacao": "Em execução", "quantidade": 6, "percentual": 46.15 }]
  },
  "marcosCriticosComposicao": {
    "porSituacao": [{ "situacao": "No prazo", "quantidade": 5, "percentual": 18.52 }],
    "porTipo": [{ "tipo": "Licitação", "quantidade": 3, "percentual": 11.11 }]
  },
  "projetosPorInstituicao": [{ "instituicao": "SEPLAG", "projetos": 2, "percentual": 50.0 }],
  "territorio": {
    "totalMunicipios": 9,
    "municipios": [{ "id": 701, "nome": "João Pessoa", "entregas": 3 }]
  },
  "valorPorUnidadeOrcamentaria": [
    { "codigo": 21101, "nome": "SEPLAG", "estimado": 300000.0, "reprogramado": 300000.0, "realizado": 571544.48 }
  ],
  "projetos": [
    {
      "id": 514, "nome": "DIGITALIZA PB", "area": "Paraíba Inovadora",
      "estimado": 350000.0, "pagoAteHoje": 621544.48,
      "marcosAtrasados": 1, "marcosTotal": 14, "percentualMarcosAtrasados": 7.14,
      "atrasos": [
        { "tipo": "ENTREGA", "id": 928, "nome": "teste", "dataPrevista": "2026-09-23", "diasAtraso": 44 },
        { "tipo": "MARCO", "id": 1209, "nome": "Abertura do ponto Bancarios", "entrega": "Abertura de pontos municipais",
          "dataPrevista": "2026-06-19", "diasAtraso": 140 }
      ]
    }
  ]
}
```

- Tipos de entrega: Obra; Equipamento; Plano/estudo/pesquisa; Serviço; Serviços complementares; Normatização.
- Situações de entrega: Planejamento; Em execução; Paralisada; Cancelada; Concluída.
- Situações de marco: No prazo; Concluído; Concluído atrasado; Atrasado.
- Tipos de marco: Convênio; Licenciamento ambiental; Projeto de engenharia; Obra; Licitação; PPP; PMI; Sistema de
  informação; Normatização.
- `valorPorUnidadeOrcamentaria` vem ordenado pelo maior estimado.
- `projetosPorInstituicao` vem ordenado pela maior quantidade; projeto sem instituição aparece em "Não informada".

### 4.3 `GET /painel/executivo/entregas` (aba Entregas realizadas, SD #10796)

Parâmetros a mais: `periodoInicio` e `periodoFim` (data de conclusão) e `modo` (`CONCLUIDAS` ou `PROXIMOS_DIAS`).

```json
{
  "dataPosicao": "2026-11-06",
  "projetosNaCarteira": { "...": "igual ao da carteira" },
  "concluidasNaCarteira": { "concluidas": 3, "registradas": 13, "percentual": 23.08 },
  "concluidasNoPeriodo": { "concluidas": 2, "registradas": 13, "percentual": 15.38 },
  "territorio": { "totalMunicipios": 2, "municipios": [{ "id": 701, "nome": "João Pessoa", "entregas": 1 }] },
  "projetos": [
    {
      "id": 514, "nome": "DIGITALIZA PB", "instituicao": "SEPLAG",
      "concluidas": 2, "percentualConclusao": 40.0,
      "proximasEntregas": 1, "dataProximaEntrega": "2026-12-01",
      "entregas": [{ "id": 1200, "nome": "Abertura de pontos municipais", "tipo": "Serviço", "local": "João Pessoa", "data": "2026-08-14" }]
    }
  ]
}
```

- `concluidasNaCarteira` e `projetosNaCarteira` ignoram o período; `concluidasNoPeriodo`, `territorio` e `projetos`
  respeitam o período.
- `modo=CONCLUIDAS`: `entregas` traz só as concluídas no período, com a data de conclusão; projetos ordenados pelo maior
  `percentualConclusao`.
- `modo=PROXIMOS_DIAS`: `entregas` traz só as previstas nos 60 dias seguintes à posição, com a data prevista; projetos
  ordenados pela `dataProximaEntrega` mais próxima.

### 4.4 `GET /painel/executivo/onde-atuar` (aba Onde atuar, SD #10797)

```json
{
  "dataPosicao": "2026-11-06",
  "projetosNaCarteira": { "...": "igual ao da carteira" },
  "financeiro": { "estimado": 350000.0, "reprogramado": 350000.0, "realizado": 621544.48 },
  "indiceCumprimento": { "noPrazo": 1, "atencao": 1, "critico": 1, "foraDoUniverso": 1 },
  "territorio": {
    "municipios": [{ "id": 701, "nome": "João Pessoa", "projetos": 2, "projetosCriticos": 1, "percentualCriticos": 50.0 }]
  },
  "projetos": [
    {
      "id": 514, "nome": "DIGITALIZA PB", "dataConclusao": "2027-06-30", "instituicao": "SEPLAG",
      "estimado": 350000.0, "realizadoAteHoje": 621544.48,
      "marcosConcluidos": 6, "marcosVencidos": 10, "indice": 60.0, "faixa": "ATENCAO",
      "marcosAtrasados": [{ "id": 1209, "nome": "Abertura do ponto Bancarios", "entrega": "Abertura de pontos municipais", "dataPrevista": "2026-06-19", "diasAtraso": 140 }]
    }
  ]
}
```

- **Índice de cumprimento** = marcos concluídos até a posição ÷ marcos com data prevista até a posição.
- Faixas: `NO_PRAZO` (≥ 80%), `ATENCAO` (60% a 79%), `CRITICO` (< 60%).
- Projeto sem marco previsto até a posição fica fora: não entra na contagem nem na tabela (conta em `foraDoUniverso`).
- `projetos` vem ordenado do pior índice para o melhor.
- Na tabela, `dataConclusao` é a maior data de término prevista entre as entregas do projeto.
- No território, `percentualCriticos` = projetos críticos do município ÷ projetos do município (o município é o local da
  entrega associada ao marco).

### 4.5 `GET /painel/projetos/{id}` (visão por projeto, SD #10798)

Parâmetros: `horizonte` e `dataPosicao`. Projeto fora do escopo: `404`.

```json
{
  "dataPosicao": "2026-11-06",
  "posicaoNaCarteira": { "indice": 2, "total": 4 },
  "projeto": {
    "id": 514, "nome": "DIGITALIZA PB", "status": "Execução", "area": "Paraíba Inovadora",
    "dataInicio": "2026-01-01", "dataPrevistaTermino": "2027-06-30",
    "gerente": "Eduardo Ribeiro", "instituicao": "SEPLAG"
  },
  "indiceCumprimento": { "marcosConcluidos": 6, "marcosVencidos": 10, "indice": 60.0, "faixa": "ATENCAO" },
  "entregas": {
    "concluidas": 2, "total": 5,
    "porSituacao": [{ "situacao": "Em execução", "quantidade": 3 }]
  },
  "financeiro": { "estimado": 350000.0, "reprogramado": 350000.0, "realizado": 621544.48 },
  "municipios": { "total": 2, "entregas": 4, "lista": [{ "id": 701, "nome": "João Pessoa", "entregas": 3 }] },
  "alinhamento": {
    "objetivos": [{ "codigo": "OBJ15", "nome": "Elevar a capacidade de inovação..." }],
    "estrategias": ["..."], "metas": ["..."], "ods": ["8- Promover o crescimento econômico..."]
  },
  "indicadores": {
    "emAndamento": 1, "abaixoDaMeta": 1,
    "lista": [
      {
        "id": 31, "nome": "Número de sistemas implantados", "fonte": "SEPLAG",
        "emAndamento": true, "abaixoDaMeta": true,
        "metaAnoVigente": { "previsto": 1.0, "realizado": 0.0, "percentual": 0.0 },
        "metaTotal": { "previsto": 1.0, "realizado": 0.0, "percentual": 0.0 },
        "vigenciaInicio": "2026-01-01", "vigenciaFim": "2027-12-31", "descricao": "...",
        "periodos": [{ "periodo": "2026", "previsto": 0.0, "realizado": 0.0 }],
        "ultimaAtualizacao": "2026-06-25"
      }
    ]
  },
  "entregasEMarcos": [
    {
      "id": 1200, "nome": "Abertura de pontos municipais", "situacao": "Concluída", "responsavel": "Eduardo Ribeiro",
      "previsaoConclusao": "2026-08-14", "atrasada": false, "local": "João Pessoa",
      "fisico": { "estimado": 8, "reprogramado": 13, "realizado": 10, "unidade": "Un" },
      "financeiro": { "estimado": 300000.0, "reprogramado": 300000.0, "realizado": 571544.48 },
      "avisos": ["Escopo físico reprogramado excedente ao planejado", "Realização de custo excedida acima de 25%"],
      "marcos": [{ "id": 1209, "nome": "Abertura do ponto Bancarios", "dataPrevista": "2026-06-19", "situacao": "Atrasado", "diasAtraso": 140 }]
    }
  ],
  "riscos": {
    "ALTA": [
      {
        "id": 1845, "nome": "Mudança na legislação", "natureza": "AMEACA", "dataProvavel": "2026-12-01", "status": "ABERTO",
        "descricao": "...",
        "planoResposta": { "nome": "...", "estrategia": "MITIGAR", "responsavel": "...", "situacao": "EM_ANDAMENTO", "quando": "PRE_OCORRENCIA" }
      }
    ],
    "MEDIA": [], "BAIXA": []
  },
  "ocorrencias": { "ALTA": [{ "id": 2949, "nome": "teste", "natureza": "PROBLEMA", "status": "ABERTA", "descricao": "..." }], "MEDIA": [], "BAIXA": [] }
}
```

- `indiceCumprimento` usa a mesma regra do item 4.4.
- `responsavel` da entrega é o gerente da entrega (papel "Gerente" nas partes interessadas).
- `avisos`: "Escopo concluído", "Escopo físico reprogramado excedente ao planejado" e "Realização de custo
  excedida acima de 25%", pelas mesmas regras do cronograma do OpenPMO.
- Traduções: `natureza` AMEACA, OPORTUNIDADE, PROBLEMA, BENEFICIO; `status` ABERTO, OCORRIDO, NAO_OCORRERA, FECHADO;
  `estrategia` MITIGAR, TRANSFERIR, ACEITAR, ELIMINAR, ESCALAR, EXPLORAR, MELHORAR, COMPARTILHAR.
- `posicaoNaCarteira` considera os filtros recebidos.

## 5. Fora desta API

- Telas, gráficos e exportação PDF/Excel ficam no painel.
- O controle de quem pode abrir o painel fica com o painel: a API devolve a carteira inteira dentro do escopo.
