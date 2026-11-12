# Rodando o OpenPMO na sua máquina

Este guia leva você do zero até o OpenPMO aberto no navegador, logado e com dados, pronto para
programar. Não precisa instalar Java, Node, Neo4j nem Keycloak: tudo roda em containers.

Tempo estimado: **15 a 25 minutos na primeira vez** (a maior parte é download e compilação). Depois disso,
o ambiente sobe em menos de 1 minuto.

---

## 1. O que você precisa ter instalado

| Ferramenta | Como conferir | Onde baixar |
|---|---|---|
| Git | `git --version` | https://git-scm.com/downloads |
| Docker Desktop (com Compose v2) | `docker compose version` | https://www.docker.com/products/docker-desktop |

No Docker Desktop, em **Settings → Resources → Memory**, reserve memória para o Docker:

| Uso | Memória do Docker |
|---|---|
| Só rodar o sistema (porta 4200) | **4 GB** |
| Programar o front com atualização automática (porta 4300, passo 6) | **6 GB ou mais** |

Deixe também **20 GB de disco**. Confira quanto o Docker tem hoje:

```bash
docker info --format '{{.MemTotal}}' | awk '{printf "%.1f GB\n", $1/1024/1024/1024}'
```

Confira se o Docker está ligado:

```bash
docker info > /dev/null && echo "Docker OK"
```

Precisa aparecer `Docker OK`. Se der erro, abra o Docker Desktop e espere ele terminar de iniciar.

---

## 2. Baixar o código

Os dois repositórios precisam ficar **lado a lado na mesma pasta**:

```bash
mkdir -p ~/projetos/openpmo && cd ~/projetos/openpmo
git clone -b develop https://github.com/macroplan-rj/openpmo-api.git
git clone -b develop https://github.com/macroplan-rj/openpmo-web.git
```

Resultado esperado:

```
~/projetos/openpmo/
├── openpmo-api/   ← back-end (Java) — aqui fica o docker-compose.yml
└── openpmo-web/   ← front-end (Angular)
```

> Peça acesso à organização `macroplan-rj` no GitHub se o clone pedir senha ou der "not found".

---

## 3. Subir o ambiente

```bash
cd ~/projetos/openpmo/openpmo-api
docker compose up -d --build
```

Na primeira vez o Docker baixa as imagens e compila a API e o front. Você vai ver muitas linhas de
log; é normal. No final aparece algo assim:

```
 ✔ Container openpmo-neo4j-seed-1  Exited
 ✔ Container openpmo-neo4j-1       Healthy
 ✔ Container openpmo-keycloak-1    Healthy
 ✔ Container openpmo-api-1         Healthy
 ✔ Container openpmo-web-1         Started
```

Confira o estado a qualquer momento:

```bash
docker compose ps
```

Todos devem estar `running` (e `healthy` onde houver). O `neo4j-seed` não aparece nessa lista: ele só
carrega os dados no banco e termina. Para vê-lo, use `docker compose ps -a` (status `Exited (0)` é o normal).

---

## 4. Conferir se está tudo certo

```bash
docker/smoke-test.sh
```

Resultado esperado:

```
OpenPMO local — smoke test
  OK   web no ar (http://localhost:4200)
  OK   API no ar (via proxy do web)
  OK   Neo4j com o seed (3304 nós)
  OK   Keycloak emitiu token para admin@openpmo.dev
  OK   API reconheceu o usuário (sessão OpenPMO emitida)
  OK   escritórios visíveis para o admin (1)
  OK   botão Entrar redireciona para o Keycloak local
Tudo certo. Acesse http://localhost:4200 e entre com admin@openpmo.dev / openpmo.
```

> No Windows, rode os scripts `.sh` pelo **Git Bash** ou pelo **WSL**.

---

## 5. Entrar no sistema

1. Abra **http://localhost:4200**
2. Clique em **Entrar**. Você vai para a tela de login do Keycloak local.
3. Usuário **`admin`** e senha **`openpmo`**.
4. Você volta para o OpenPMO já logado, com o escritório, os planos e os projetos do seed.

### Usuários de teste

Todos com a senha **`openpmo`**. Entre pelo usuário (`pessoa04`) ou pelo e-mail (`pessoa04@openpmo.dev`).

| Usuário | Perfil para testar |
|---|---|
| `admin` | administrador, vê tudo |
| `pessoa03`, `pessoa05` | administrador que também é membro de escritório, plano e projetos |
| `pessoa04` | usuário comum com edição no escritório, plano e projetos |
| `pessoa07`, `pessoa12`, `pessoa13` | edição no escritório e em um projeto |
| `pessoa10`, `pessoa11`, `pessoa15`, `pessoa20` | edição só no escritório |
| `pessoa08` | acesso ao plano e projetos, sem escritório |
| `pessoa18` | acesso a um único projeto |
| `pessoa02`, `pessoa06`, `pessoa09`, `pessoa14`, `pessoa17`, `pessoa19` | sem permissões (para testar "sem acesso") |

### Outros endereços

| O quê | Endereço | Login |
|---|---|---|
| API | http://localhost:8080/openpmo | (pelo front) |
| Neo4j Browser (banco) | http://localhost:7474 | `neo4j` / `openpmo` |
| Keycloak (console) | http://localhost:8180 | `admin` / `admin` |

---

## 6. Programando

### Front-end com atualização automática (recomendado)

Suba o modo de desenvolvimento do front:

```bash
docker compose --profile dev up -d web-dev
docker compose logs -f web-dev
```

Na primeira vez ele instala as dependências (de 3 a 8 minutos). Quando aparecer
`Compiled successfully`, pressione `Ctrl+C` para sair dos logs e abra **http://localhost:4300**.

> Precisa de **6 GB de memória no Docker** (passo 1). Com menos, o `web-dev` é encerrado no meio da
> compilação (veja "Problemas comuns").

Agora é só editar os arquivos em `openpmo-web/src/` no seu editor: a página recarrega sozinha em poucos
segundos. O login funciona igual (usuário `admin`, senha `openpmo`).

> O endereço **4200** continua servindo a versão compilada; use o **4300** enquanto programa.

### Back-end (API)

Depois de alterar o código Java em `openpmo-api/src/`, recompile e reinicie só a API:

```bash
docker compose up -d --build api
```

Leva de 2 a 4 minutos. Acompanhe com `docker compose logs -f api` até aparecer `Started OpenPmoApplication`.

Testes do back-end:

```bash
docker run --rm -v "$PWD":/w -v "$HOME/.gradle":/root/.gradle -w /w eclipse-temurin:11-jdk sh ./gradlew test
```

Se preferir rodar a API pela IDE (IntelliJ/Eclipse): `docker compose stop api` e use o arquivo
`docker/api/application-local.properties`, trocando `bolt://neo4j:7687` por `bolt://localhost:7687` e
`http://keycloak:8080` por `http://localhost:8180`.

### Banco de dados

Abra http://localhost:7474, entre com `neo4j` / `openpmo` e rode consultas Cypher, por exemplo:

```cypher
MATCH (w:Workpack:Project) RETURN w.name LIMIT 25
```

---

## 7. Comandos do dia a dia

| O que fazer | Comando (dentro de `openpmo-api`) |
|---|---|
| Ligar tudo | `docker compose up -d` |
| Ligar com o front em modo dev | `docker compose --profile dev up -d` |
| Desligar (mantém os dados) | `docker compose --profile dev down` |
| Ver o estado | `docker compose ps` |
| Ver logs | `docker compose logs -f api` (ou `web`, `web-dev`, `neo4j`, `keycloak`) |
| Recompilar depois de `git pull` | `docker compose up -d --build` |
| **Voltar os dados ao estado inicial** | `docker compose --profile dev down -v && docker compose up -d` |

---

## 8. Sobre os dados

O banco sobe com `docker/neo4j/seed.dump`, carregado **só na primeira vez** (ou depois de um `down -v`).
É uma cópia da estrutura do OpenPMO (escritórios, planos, modelos, projetos, entregas e marcos) com
**todos os dados pessoais trocados por fictícios**: nomes, e-mails, telefones, endereços e textos livres.
Arquivos anexados não fazem parte do seed.

O que você cria ou altera fica salvo no volume do Docker entre um `down` e um `up`. Para começar de novo,
use o comando "Voltar os dados ao estado inicial".

**Gerar um seed novo** (só para quem mantém o ambiente):

```bash
docker/neo4j/gerar-seed.sh /caminho/do/dump-de-origem.dump
```

O script anonimiza o dump (`anonimizar.cypher`), **falha se sobrar algum e-mail real**
(`verificar-anonimizacao.cypher`) e atualiza a lista de usuários em `docker/keycloak/usuarios-seed.csv`.
Se a quantidade de pessoas mudar, atualize também `docker/keycloak/realm-openpmo.json`.
**Nunca versione o dump de origem**, só o `seed.dump` gerado.

---

## 9. Problemas comuns

| Sintoma | Causa provável | Solução |
|---|---|---|
| `unable to prepare context: path "../openpmo-web" not found` | o openpmo-web não está ao lado | refaça o passo 2 com as duas pastas lado a lado |
| `port is already allocated` | outro programa usando 4200, 4300, 8080, 7474, 7687 ou 8180 | feche o programa ou troque a porta da esquerda em `ports:` no `docker-compose.yml` |
| build do `web` falha com falta de memória | pouca memória para o Docker | aumente para 4 GB ou mais (passo 1) |
| `api` fica muito tempo como `starting` | a API está subindo (até ~1 min) | aguarde e veja `docker compose logs -f api` |
| login volta para a tela inicial sem entrar | sessão antiga no navegador | abra numa janela anônima ou limpe os cookies de `localhost` |
| tela de login mostra "não autorizado" | usuário sem cadastro no OpenPMO | use um usuário da tabela do passo 5 |
| dados bagunçados depois de testes | — | `docker compose --profile dev down -v && docker compose up -d` |
| `web-dev` não atualiza ao salvar | primeira compilação ainda rodando | veja `docker compose logs -f web-dev` e aguarde `Compiled successfully` |
| `web-dev` para sozinho e `docker compose ps -a` mostra `Exited (137)` | faltou memória para o Docker | aumente a memória do Docker para 6 GB ou mais (passo 1) e rode `docker compose --profile dev up -d web-dev` de novo |
| `web-dev` com `npm ERR! fsevents not accessible` | dependências instaladas com npm antigo | `docker compose --profile dev rm -sf web-dev && docker volume rm openpmo_web_node_modules` e suba de novo |

---

## Como funciona (para curiosos)

```
navegador ──► web (nginx :4200) ──► /openpmo ──► api (Spring Boot :8080) ──► neo4j (:7687)
    │                                                  │
    └────────────► keycloak (:8180) ◄──────────────────┘
                   login do usuário        valida o login (rede interna, keycloak:8080)
```

- **neo4j-seed** carrega o `seed.dump` no volume do banco na primeira subida e termina.
- **keycloak** importa o realm `openpmo` (`docker/keycloak/realm-openpmo.json`): client `openpmo` e os
  usuários de teste. O `id` de cada usuário é igual à chave da pessoa no seed, e é assim que a API sabe
  quem entrou.
- **api** usa a configuração `docker/api/application-local.properties`.
- **web** é o front compilado, servido pelo nginx com `docker/web/nginx.conf` e `docker/web/app-config.json`.
- **web-dev** (perfil `dev`) roda `ng serve` com o código de `../openpmo-web` montado, para hot reload.
