# estoqueIA — Contexto do projeto (backend)

> Documentação viva. Atualizar a cada mudança significativa (nova feature, endpoint, dependência, decisão arquitetural).
>
> **Última atualização:** 2026-06-07
>
> Frontend correspondente: [`../estoqueia-angular/context.md`](../estoqueia-angular/context.md)

---

## Visão geral

Sistema de gestão de estoque com IA preditiva, projeto da disciplina UNITINS.

- **Backend:** Spring Boot 4.0.6 + Java 21 + PostgreSQL
- **Frontend (futuro):** Angular (porta 4200)
- **Auth:** JWT (HMAC HS256, stateless) + refresh token rotativo
- **Migrations:** Flyway
- **Docs:** OpenAPI/Swagger
- **Observabilidade:** Spring Actuator
- **Deploy:** Docker Compose

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 4.0.6 |
| Web | Spring Web MVC |
| Persistência | Spring Data JPA + Hibernate 7 |
| Banco | PostgreSQL 16+ |
| Migrations | Flyway 11 (via `spring-boot-flyway`) |
| Segurança | Spring Security 7 + OAuth2 Resource Server (JWT) |
| Validação | Jakarta Bean Validation |
| Docs | springdoc-openapi 2.8.13 (compatível com Spring 7 / Boot 4) |
| Observabilidade | Spring Boot Actuator |
| Testes | JUnit 5 + Mockito + AssertJ + Testcontainers 1.20.4 |
| Boilerplate | Lombok |
| Build | Maven (wrapper `./mvnw`) |
| Container | Docker (multi-stage) + docker-compose |

## Como rodar

### Local (modo dev)

Pré-requisitos: Java 21, PostgreSQL rodando, banco `estoqueia` criado.

PowerShell (sessão atual):
```powershell
$env:DB_PASSWORD="123456"
./mvnw spring-boot:run
```

Permanente:
```powershell
[System.Environment]::SetEnvironmentVariable("DB_PASSWORD","123456","User")
```

### Docker Compose (recomendado p/ onboarding)

```powershell
copy .env.example .env
# editar .env com senhas reais
docker compose up --build
```

Sobe Postgres + app. Healthcheck do Postgres garante ordem correta.

### Variáveis de ambiente

| Variável | Default (dev) | Obrigatório em prod | Descrição |
|---|---|---|---|
| `DB_HOST` | `localhost` | sim | Host do Postgres |
| `DB_PORT` | `5432` | não | Porta do Postgres |
| `DB_NAME` | `estoqueia` | sim | Nome do banco |
| `DB_USER` | `postgres` | sim | Usuário |
| `DB_PASSWORD` | `123456` | sim | Senha (default dev = 123456) |
| `JWT_SECRET` | default dev | **sim** | Chave HMAC, mín. 32 chars |
| `DEEPSEEK_API_KEY` | (vazio) | p/ usar a IA | Key da DeepSeek; sem ela, `/ia/assistente` responde 422 |
| `SPRING_PROFILES_ACTIVE` | (vazio) | `prod` no Docker | Ativa application-prod.properties |

### Profile `prod`

Em `application-prod.properties` **não há defaults** — toda variável vem do ambiente. Logs sóbrios, `show-sql=false`, Actuator restrito.

## Endpoints

Base URL: `http://localhost:8080`

### Públicos (sem token)

| Método | Path | Descrição |
|---|---|---|
| POST | `/auth/register` | Cria usuário (role opcional, default USUARIO) |
| POST | `/auth/login` | Retorna `{accessToken, refreshToken, tokenType, expiresIn}` |
| POST | `/auth/refresh` | Rotação: invalida o refresh antigo, emite par novo |
| GET | `/swagger-ui.html` | UI do Swagger |
| GET | `/v3/api-docs` | Spec OpenAPI |
| GET | `/actuator/health` | Health check (sem detalhes) |
| GET | `/actuator/info` | Versão e nome |

### Usuário (auth required)

| Método | Path | Role | Descrição |
|---|---|---|---|
| GET | `/usuarios/me` | qualquer | Perfil do autenticado |
| GET | `/usuarios` | **ADMIN** | Lista paginada |
| GET | `/usuarios/{id}` | **ADMIN** | Busca por ID |
| PUT | `/usuarios/{id}` | próprio ou **ADMIN** | Atualiza (com troca de senha opcional) |
| DELETE | `/usuarios/{id}` | **ADMIN** | Desativa (soft-delete) |

### Categoria (auth required)

| Método | Path | Role | Descrição |
|---|---|---|---|
| GET | `/categorias` | qualquer | Lista paginada |
| GET | `/categorias/{id}` | qualquer | Busca por ID |
| POST | `/categorias` | **ADMIN** | Cria |
| PUT | `/categorias/{id}` | **ADMIN** | Atualiza |
| DELETE | `/categorias/{id}` | **ADMIN** | Remove |

### Produto (auth required)

| Método | Path | Role | Descrição |
|---|---|---|---|
| GET | `/produtos?nome=&page=&size=` | qualquer | Lista paginada, filtro opcional |
| GET | `/produtos/{id}` | qualquer | Busca por ID |
| GET | `/produtos/baixo-estoque` | qualquer | `quantidade < estoqueMinimo` |
| POST | `/produtos` | **ADMIN** | Cria (código auto: `PROD-XXXXXXXX`) |
| PUT | `/produtos/{id}` | **ADMIN** | Atualiza |
| DELETE | `/produtos/{id}` | **ADMIN** | Soft-delete |

### Movimentação (auth required)

| Método | Path | Role | Descrição |
|---|---|---|---|
| GET | `/movimentacoes?produtoId=` | qualquer | Paginada, recentes primeiro |
| POST | `/movimentacoes` | qualquer | Registra ENTRADA ou SAIDA. Atualiza `Produto.quantidade` e grava `usuario_id` |

Regra de negócio:
- `ENTRADA` → `quantidade += quantidade`
- `SAIDA` → `quantidade -= quantidade`; **422** se estoque insuficiente
- Produto inativo → **422**

### Previsão / IA (auth required)

| Método | Path | Role | Descrição |
|---|---|---|---|
| GET | `/previsao/produtos/{id}?janelaDias=` | qualquer | Previsão para um produto (default janela=30) |
| GET | `/previsao/reposicao-sugerida?janelaDias=` | qualquer | Lista de produtos cuja reposição é recomendada |

Algoritmo (em [PrevisaoService.java](src/main/java/unitins/gestao/estoqueIA/service/PrevisaoService.java)):
1. Soma SAÍDAS dos últimos N dias (janela)
2. `consumoMedioDiario = saidas / N`
3. `diasAteRuptura = quantidade / consumoMedioDiario`
4. Sugere reposição quando:
   - `quantidade < estoqueMinimo`, OU
   - `diasAteRuptura <= tempoReposicaoDias`

**Inteligência adicional (v2)** — campos extra no `PrevisaoResponse`:
- `consumoMedioPonderado` — média móvel exponencial (EWMA, α=0.3): reage mais rápido a mudanças recentes.
- `tendencia` — `ALTA` | `BAIXA` | `ESTAVEL` | `SEM_DADOS` (compara 1ª vs 2ª metade da janela).
- `quantidadeSugerida` — quanto comprar = cobrir o lead time + repor o estoque mínimo (≥ 0).
- `nivelConfianca` — `ALTA` (≥10 movs) | `MEDIA` (3–9) | `BAIXA` (<3) na janela.
- `movimentacoesSaidaNaJanela` — nº de saídas observadas.

> **Status:** v2 (média móvel simples + EWMA + tendência + quantidade sugerida). Sem mudança de schema. Próximo: sazonalidade / ML.

### Análise de estoque (auth required)

| Método | Path | Role | Descrição |
|---|---|---|---|
| GET | `/analise/resumo` | qualquer | Visão executiva: totais, valor imobilizado, nº abaixo do mínimo, categoria mais crítica |
| GET | `/analise/curva-abc` | qualquer | Curva ABC por valor imobilizado (classes A≤80%, B≤95%, C resto) |
| GET | `/analise/anomalias?janelaDias=` | qualquer | `PICO_SAIDA` (saída >5× consumo médio) e `ESTOQUE_PARADO` (sem saída, mas com estoque) |

Tudo determinístico, em [EstoqueAnaliseService.java](src/main/java/unitins/gestao/estoqueIA/service/EstoqueAnaliseService.java). Serve ao dashboard **e** à IA (como ferramentas/contexto).

### Assistente de IA — LLM (auth required)

| Método | Path | Role | Descrição |
|---|---|---|---|
| POST | `/ia/assistente` | qualquer | Pergunta única sobre reposição; responde via DeepSeek |
| POST | `/ia/chat` | qualquer | Chat de dúvidas multi-turno (uso do sistema + dados do estoque, contexto fixo) |
| POST | `/ia/agente` | qualquer | **Agente com ferramentas**: consulta o sistema sob demanda (function calling) |
| GET | `/ia/resumo` | qualquer | Resumo executivo do estoque em linguagem natural |
| GET | `/ia/pedido-compra` | qualquer | Rascunho de pedido de compra a partir das reposições sugeridas |
| POST | `/ia/movimentacao-nl` | qualquer | Interpreta "dei baixa de 10 mouses" → preview `{produtoId, tipo, quantidade}` (não grava) |

`/ia/assistente` → `{ "pergunta": "o que devo repor essa semana?" }` → `{ "resposta": "..." }`

`/ia/chat` e `/ia/agente` → `{ "mensagens": [{ "papel": "user", "conteudo": "..." }, ...] }` → `{ "resposta": "..." }`
Backend **stateless**: o cliente envia o histórico inteiro a cada chamada (`papel` é `user` ou `assistant`).

`/ia/movimentacao-nl` → `{ "texto": "saída de 10 unidades de Mouse" }` →
`{ "interpretado": true, "produtoId": 1, "produtoNome": "Mouse", "tipo": "SAIDA", "quantidade": 10, "mensagem": "..." }`.
É só pré-visualização: para efetivar, o cliente confirma e chama `POST /movimentacoes`.

**Chat com contexto fixo** ([IaAssistenteService.java](src/main/java/unitins/gestao/estoqueIA/service/ia/IaAssistenteService.java)):
1. Busca o contexto real via `PrevisaoService.reposicaoSugerida()` (números determinísticos)
2. Injeta esse contexto + a pergunta num prompt
3. Chama a DeepSeek via `RestClient` (API compatível com OpenAI, `POST /chat/completions`)
4. O LLM apenas **interpreta/prioriza/explica** — não inventa estoque

**Agente com ferramentas** ([IaAgenteService.java](src/main/java/unitins/gestao/estoqueIA/service/ia/IaAgenteService.java)):
O LLM recebe definições de **ferramentas** e decide quais chamar. Loop (máx. 5 iterações): modelo
pede `tool_calls` → executamos os serviços reais → devolvemos o JSON do resultado → modelo conclui.
Ferramentas: `resumo_estoque`, `reposicao_sugerida`, `prever_produto`, `buscar_produto`,
`curva_abc`, `anomalias`, `historico_movimentacao`. O suporte a function calling está no
[DeepSeekClient.java](src/main/java/unitins/gestao/estoqueIA/service/ia/DeepSeekClient.java).

> Requer `DEEPSEEK_API_KEY` no ambiente. Modelo default `deepseek-chat` (`deepseek.model`). Sem key → 422 com aviso.

## Fluxo de autenticação

1. **Registrar** (`POST /auth/register`):
   ```json
   { "nome": "Rafael", "email": "rafael@teste.com", "senha": "senha123", "role": "ADMIN" }
   ```

2. **Login** (`POST /auth/login`):
   ```json
   { "email": "rafael@teste.com", "senha": "senha123" }
   ```
   Retorna:
   ```json
   {
     "accessToken": "eyJ...",
     "refreshToken": "AbCd...",
     "tokenType": "Bearer",
     "expiresIn": 3600
   }
   ```

3. **Requisições autenticadas:** header `Authorization: Bearer <accessToken>`

4. **Renovar token** (`POST /auth/refresh`):
   ```json
   { "refreshToken": "AbCd..." }
   ```
   Retorna novo par. O refresh antigo é **revogado** (rotação).

JWT claims:
- `iss=estoqueIA`, `sub=email`, `scope=ADMIN|USUARIO`, `iat`, `exp`

Refresh token:
- 64 bytes aleatórios base64url
- Persistido em `refresh_token` com `expira_em` (default: 30 dias)
- Rotação obrigatória em `/auth/refresh`

## Roles

| Role | Pode |
|---|---|
| `USUARIO` | Ler tudo, registrar movimentações, ver previsão, alterar próprio perfil |
| `ADMIN` | Tudo do USUARIO + CRUD de Categoria/Produto/Usuário, Actuator |

Implementado via `@PreAuthorize("hasRole('ADMIN')")` nos endpoints sensíveis.

## Estrutura de pastas

```
src/main/java/unitins/gestao/estoqueIA/
├── EstoqueIaApplication.java
├── config/
│   └── OpenApiConfig.java          # SecurityScheme bearerAuth
├── controller/
│   ├── AuthController.java         # /auth/{login,register,refresh}
│   ├── UsuarioController.java      # /usuarios + /usuarios/me
│   ├── CategoriaController.java
│   ├── ProdutoController.java
│   ├── MovimentacaoController.java
│   ├── PrevisaoController.java     # /previsao/*
│   ├── AnaliseController.java      # /analise/* (resumo, curva ABC, anomalias)
│   └── IaController.java           # /ia/* (assistente, chat, agente, resumo, pedido, mov-nl)
├── dto/
│   ├── auth/      # LoginRequest, RegisterRequest, RefreshRequest, TokenResponse
│   ├── usuario/   # UsuarioResponse, UsuarioUpdateRequest
│   ├── categoria/
│   ├── produto/
│   ├── movimentacao/
│   ├── previsao/  # PrevisaoResponse
│   └── ia/        # AssistenteRequest/Response, ChatRequest/Response, ChatMessage
├── entity/
│   ├── enums/                      # Role, TipoMovimentacao
│   ├── Usuario.java
│   ├── RefreshToken.java
│   ├── Categoria.java
│   ├── Produto.java                # @PrePersist gera código
│   └── Movimentacao.java
├── exception/
│   ├── NotFoundException.java       # → 404
│   ├── ConflictException.java       # → 409
│   ├── BusinessException.java       # → 422
│   └── ApiExceptionHandler.java     # @RestControllerAdvice + AccessDenied
├── repository/
├── security/
│   ├── SecurityConfig.java          # Filter chain, CORS, JWT, BCrypt, @EnableMethodSecurity
│   ├── UsuarioDetailsService.java
│   ├── TokenService.java            # Gera JWT HS256
│   └── RefreshTokenService.java     # Emite/rotaciona/revoga
└── service/
    ├── (Usuario/Categoria/Produto/Movimentacao/Previsao)Service.java
    ├── EstoqueAnaliseService.java    # Resumo, curva ABC, anomalias (determinístico)
    └── ia/
        ├── DeepSeekClient.java       # RestClient p/ DeepSeek (formato OpenAI) + function calling
        ├── IaAssistenteService.java  # Chat com contexto fixo + resumo/pedido/movimentação-NL
        └── IaAgenteService.java      # Agente com ferramentas (loop de tool calling)

src/main/resources/
├── application.properties            # dev
├── application-prod.properties       # prod (só env vars)
└── db/migration/
    ├── V1__init.sql                  # schema inicial
    └── V2__refresh_token.sql

src/test/java/unitins/gestao/estoqueIA/
├── service/
│   ├── MovimentacaoServiceTest.java  # 4 testes — regra de estoque
│   └── PrevisaoServiceTest.java      # 4 testes — algoritmo de previsão
└── AuthFlowIntegrationTest.java      # Testcontainers — requer Docker
```

## Decisões arquiteturais

- **DTO separado de Entity** em todas as camadas → não vazar campos internos.
- **Service contém regra de negócio**, controller só REST.
- **Soft-delete em Produto e Usuário** (`ativo=false`) → preserva integridade.
- **Movimentação sem PUT/DELETE** → histórico imutável (auditoria).
- **Flyway controla schema**, Hibernate só valida (`ddl-auto=validate`).
- **JWT HMAC simétrico** (HS256) → simples, suficiente para um único serviço.
- **Refresh token opaco** (não JWT) → revogável por DB.
- **Rotação de refresh** → emitir novo invalida o antigo, mitigando reuso.
- **CORS** liberado apenas para `http://localhost:4200` (dev do Angular).
- **Previsão = média móvel + EWMA** → simples e testável; EWMA adiciona reatividade sem schema novo.
- **Números determinísticos, IA só interpreta** → previsão/análise calculadas em Java; o LLM nunca inventa estoque. No agente, a IA acessa esses números via **function calling** (ferramentas), não por contexto colado.
- **IA não grava direto** → `/ia/movimentacao-nl` devolve preview; a escrita continua passando por `POST /movimentacoes` com suas regras.
- **springdoc 2.8.13** → versões ≤2.6 quebram com `NoSuchMethodError` em Spring 7.
- **`spring-boot-flyway`** (não `flyway-core` direto) → necessário pra auto-config do Spring Boot 4.

## Testes

- **8 unit tests** (MovimentacaoService, PrevisaoService) — rodam com `./mvnw test -Dtest='*ServiceTest'`
- **1 teste de integração** (AuthFlowIntegrationTest) — `Testcontainers` com Postgres; requer **Docker rodando**

## Tarefas futuras

- [x] ~~Frontend Angular consumindo a API~~ → ver [`../estoqueia-angular/context.md`](../estoqueia-angular/context.md)
- [x] ~~Suavização exponencial (EWMA) + tendência + quantidade sugerida + confiança~~ (v2)
- [x] ~~Análise de estoque (resumo executivo, curva ABC, anomalias)~~ → `/analise/*`
- [x] ~~IA como agente com function calling~~ → `/ia/agente`
- [ ] Algoritmo de previsão ainda mais sofisticado (sazonalidade / regressão / ML)
- [ ] Endpoint de gráficos para o dashboard (séries temporais)
- [ ] Job agendado para envio de alertas de baixo estoque (email/Telegram)
- [ ] CI (GitHub Actions): build + testes em PR
- [ ] Auditoria genérica (`@CreatedBy`, `@LastModifiedBy`) via Spring Data Auditing
- [ ] Migrar para `spring-boot-starter-test` completo quando SB4 estabilizar (resolver `TestRestTemplate`)

## Notas operacionais

### Swagger
http://localhost:8080/swagger-ui.html → "Authorize" → `Bearer <token>` do login

### Recriar banco do zero
```powershell
$env:PGPASSWORD="123456"
psql -U postgres -h localhost -d estoqueia -c "DROP TABLE IF EXISTS movimentacao, produto, categoria, refresh_token, usuario, flyway_schema_history CASCADE;"
./mvnw spring-boot:run
```

### Seed de demonstração
[`scripts/seed-demo.sql`](scripts/seed-demo.sql) popula 8 categorias, ~42 produtos e ~1.600 movimentações
(entradas + saídas) com datas espalhadas nos últimos 40 dias, calibradas para exercitar a previsão:
gera os 4 cenários da IA (abaixo do mínimo, ruptura próxima, estoque adequado, consumo zero).
Idempotente por nome de produto (rodar de novo não duplica). Requer o usuário `admin@teste.com` (ou cai no 1º usuário).
```powershell
$env:PGPASSWORD="123456"
psql -U postgres -h localhost -d estoqueia -f scripts/seed-demo.sql
```

### Logs/queries SQL (dev)
`spring.jpa.show-sql=true` habilitado. Em prod, sobreposto para `false`.

### Actuator
- `GET /actuator/health` — público, sem detalhes
- `GET /actuator/metrics` — exige ADMIN
- Configuração de exposição em `application.properties`
