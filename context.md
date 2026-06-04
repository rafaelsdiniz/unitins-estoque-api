# estoqueIA — Contexto do projeto (backend)

> Documentação viva. Atualizar a cada mudança significativa (nova feature, endpoint, dependência, decisão arquitetural).
>
> **Última atualização:** 2026-06-03
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
| `DB_PASSWORD` | `postgres` | sim | Senha |
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

> **Status:** v1 simples (média móvel). Próximas iterações podem usar suavização exponencial, sazonalidade ou modelos ML.

### Assistente de IA — LLM (auth required)

| Método | Path | Role | Descrição |
|---|---|---|---|
| POST | `/ia/assistente` | qualquer | Pergunta única sobre reposição; responde via DeepSeek |
| POST | `/ia/chat` | qualquer | Chat de dúvidas multi-turno (uso do sistema + dados do estoque) |

`/ia/assistente` → `{ "pergunta": "o que devo repor essa semana?" }` → `{ "resposta": "..." }`

`/ia/chat` → `{ "mensagens": [{ "papel": "user", "conteudo": "..." }, { "papel": "assistant", "conteudo": "..." }] }` → `{ "resposta": "..." }`
O backend é **stateless**: o cliente envia o histórico inteiro a cada chamada (máx. 30 mensagens; `papel` é `user` ou `assistant`). O system prompt descreve o sistema **e** injeta o contexto de reposição.

Fluxo (em [IaAssistenteService.java](src/main/java/unitins/gestao/estoqueIA/service/ia/IaAssistenteService.java)):
1. Busca o contexto real via `PrevisaoService.reposicaoSugerida()` (números determinísticos)
2. Injeta esse contexto + a pergunta num prompt
3. Chama a DeepSeek ([DeepSeekClient.java](src/main/java/unitins/gestao/estoqueIA/service/ia/DeepSeekClient.java)) via `RestClient` (API compatível com OpenAI, `POST /chat/completions`)
4. O LLM apenas **interpreta/prioriza/explica** — não inventa estoque

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
│   └── IaController.java           # /ia/assistente (LLM DeepSeek)
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
    └── ia/
        ├── DeepSeekClient.java       # RestClient p/ DeepSeek (formato OpenAI)
        └── IaAssistenteService.java  # Monta contexto + prompt, chama o LLM

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
- **Previsão v1 = média móvel** → algoritmo simples, defensável e testável.
- **springdoc 2.8.13** → versões ≤2.6 quebram com `NoSuchMethodError` em Spring 7.
- **`spring-boot-flyway`** (não `flyway-core` direto) → necessário pra auto-config do Spring Boot 4.

## Testes

- **8 unit tests** (MovimentacaoService, PrevisaoService) — rodam com `./mvnw test -Dtest='*ServiceTest'`
- **1 teste de integração** (AuthFlowIntegrationTest) — `Testcontainers` com Postgres; requer **Docker rodando**

## Tarefas futuras

- [x] ~~Frontend Angular consumindo a API~~ → ver [`../estoqueia-angular/context.md`](../estoqueia-angular/context.md)
- [ ] Algoritmo de previsão mais sofisticado (suavização exponencial / regressão)
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
