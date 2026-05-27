# estoqueIA — Documentação do Projeto

> Documento explicativo (visão geral + funcionamento da IA).
> Para a referência técnica detalhada (endpoints, env vars, estrutura de pastas), veja [`context.md`](context.md).

---

## 1. O que é o projeto

O **estoqueIA** é um sistema de **gestão de estoque com previsão inteligente de reposição**, desenvolvido como projeto da disciplina (UNITINS).

A ideia central: além de fazer o CRUD tradicional de um estoque (cadastrar produtos, registrar entradas e saídas), o sistema **analisa o histórico de consumo** de cada produto e **avisa quais precisam ser repostos antes que acabem**. Em vez de o gestor descobrir que um produto zerou só quando um cliente pede, o sistema antecipa isso.

### Para que serve, na prática

- Cadastrar **produtos**, organizados em **categorias**.
- Registrar **movimentações** de estoque: **ENTRADA** (compra/reabastecimento) e **SAÍDA** (venda/consumo).
- Consultar **quais produtos estão com estoque baixo**.
- Receber uma **lista de reposição sugerida** calculada pela IA, com o motivo de cada sugestão.
- Tudo protegido por **login (JWT)** com dois perfis: `ADMIN` (gerencia tudo) e `USUARIO` (consulta e registra movimentações).

---

## 2. Como o sistema é organizado (visão geral)

```
┌─────────────────┐        HTTP/JSON         ┌──────────────────────┐        ┌──────────────┐
│   Frontend      │  ───────────────────▶    │   Backend (API)      │  ───▶  │  PostgreSQL  │
│   Angular       │   Authorization: Bearer  │   Spring Boot 4      │        │   (dados)    │
│   (porta 4200)  │  ◀───────────────────    │   (porta 8080)       │  ◀───  │              │
└─────────────────┘                          └──────────────────────┘        └──────────────┘
```

- **Frontend (Angular):** a tela com que o usuário interage.
- **Backend (Spring Boot):** recebe as requisições, aplica as regras de negócio e **calcula a previsão**. É aqui que mora a "IA".
- **Banco (PostgreSQL):** guarda usuários, produtos, categorias e o **histórico de movimentações** — que é a matéria-prima da previsão.

O backend é dividido em camadas:

| Camada | Papel |
|---|---|
| **Controller** | Recebe a requisição HTTP e devolve a resposta (a "porta de entrada"). |
| **Service** | Contém as **regras de negócio** e os **cálculos** (incluindo o da IA). |
| **Repository** | Conversa com o banco de dados (busca e grava). |
| **Entity / DTO** | Representam os dados (a tabela e o "pacote" que trafega na API). |

> O cálculo da previsão fica em [`PrevisaoService.java`](src/main/java/unitins/gestao/estoqueIA/service/PrevisaoService.java).

---

## 3. Conceitos do domínio

Para entender a IA, três conceitos importam:

- **Quantidade atual** — quantas unidades existem hoje no estoque do produto.
- **Estoque mínimo** — o "piso" que o gestor definiu. Abaixo dele, considera-se que o produto está em falta.
- **Tempo de reposição (dias)** — quantos dias demora, em média, entre pedir o produto ao fornecedor e ele chegar. Se um produto demora 7 dias para chegar, ele precisa ser pedido **antes** de faltar 7 dias de estoque.

E o histórico:

- **Movimentação de SAÍDA** — cada venda/consumo registra uma saída com data e quantidade. **É esse histórico que alimenta a previsão.**

---

## 4. Como a IA funciona 🧠

### 4.1. Em uma frase

> A IA olha **quanto o produto vendeu nos últimos dias**, calcula o **ritmo médio de consumo por dia**, estima **em quantos dias o estoque vai acabar** e compara isso com **o tempo que leva para repor**. Se vai acabar antes de dar tempo de repor (ou se já está abaixo do mínimo), ela **sugere a reposição**.

### 4.2. O algoritmo passo a passo

A técnica usada é a **média móvel** — um método estatístico simples, clássico em previsão de demanda. Não é uma rede neural; é um modelo transparente e explicável, o que é uma **vantagem** para um sistema de estoque (dá para justificar cada sugestão).

Dado um produto e uma **janela** de N dias (padrão = **30 dias**):

**Passo 1 — Somar o consumo da janela**
Soma a quantidade de todas as **SAÍDAS** dos últimos N dias.
```
saidasNaJanela = soma das saídas dos últimos N dias
```

**Passo 2 — Calcular o consumo médio diário**
```
consumoMedioDiario = saidasNaJanela / N
```
Isso responde: "em média, quantas unidades saem por dia?"

**Passo 3 — Estimar dias até a ruptura**
"Ruptura" = o estoque chegar a zero.
```
diasAteRuptura = quantidadeAtual / consumoMedioDiario
```
Responde: "se o ritmo continuar, em quantos dias zera?"

**Passo 4 — Decidir se sugere reposição**
A IA sugere repor se **qualquer uma** destas condições for verdadeira:

1. **Já está abaixo do mínimo:** `quantidadeAtual < estoqueMinimo`
   → urgência imediata.
2. **A ruptura vem antes da reposição:** `diasAteRuptura <= tempoReposicaoDias`
   → se vai acabar em 5 dias mas o fornecedor demora 7, é preciso pedir **agora**.

```
sugereReposicao = (quantidade < estoqueMinimo)  OU  (diasAteRuptura <= tempoReposicao)
```

Cada resposta vem com um **motivo** em texto, explicando qual condição disparou.

### 4.3. Exemplo numérico real (produto "Arroz Branco 5kg")

Estes são os números reais retornados pela API no teste:

| Campo | Valor |
|---|---|
| Quantidade atual | **35** unidades |
| Estoque mínimo | 30 |
| Tempo de reposição | **7 dias** |
| Janela analisada | 30 dias |
| Saídas na janela | **179** unidades |

Aplicando o algoritmo:

```
consumoMedioDiario = 179 / 30           = 5,97 unidades/dia
diasAteRuptura     = 35 / 5,97          ≈ 5 dias
```

Decisão:
- Está abaixo do mínimo? `35 < 30` → **não**.
- A ruptura vem antes da reposição? `5 <= 7` → **SIM**.

➡️ **Resultado: sugere reposição.**
➡️ **Motivo:** *"Ruptura prevista em 5 dia(s); tempo de reposição = 7"*.

Ou seja: o arroz vai acabar em ~5 dias, mas demora 7 para chegar. Se esperar, o estoque zera antes da entrega — então o sistema avisa para comprar já.

### 4.4. Os quatro cenários possíveis

Dependendo dos números, um produto cai em um destes casos:

| Cenário | Condição | Sugere repor? | Motivo exibido |
|---|---|---|---|
| **Abaixo do mínimo** | `quantidade < estoqueMinimo` | ✅ Sim | "Quantidade abaixo do estoque mínimo" |
| **Ruptura próxima** | `diasAteRuptura <= tempoReposicao` | ✅ Sim | "Ruptura prevista em X dia(s)…" |
| **Estoque adequado** | nenhuma das acima | ❌ Não | "Estoque adequado" |
| **Consumo zero** | sem saídas na janela | ❌ Não | "Sem saídas na janela — consumo zero" |

> O caso **"consumo zero"** é importante: se um produto não teve nenhuma saída, não dá para estimar quando vai acabar (divisão por zero). O sistema trata isso e simplesmente não sugere reposição — afinal, se ninguém está consumindo, não há urgência.

### 4.5. Onde a IA é exposta (endpoints)

| Método | Endpoint | O que faz |
|---|---|---|
| `GET` | `/previsao/produtos/{id}?janelaDias=30` | Previsão detalhada de **um** produto. |
| `GET` | `/previsao/reposicao-sugerida?janelaDias=30` | Lista **apenas** os produtos que precisam de reposição. |

O parâmetro `janelaDias` é opcional (padrão 30). Aumentar a janela suaviza picos pontuais; diminuir torna a previsão mais sensível a mudanças recentes de demanda.

---

## 5. Por que esse método (e não uma rede neural)?

Para o contexto do projeto, a média móvel foi escolhida de propósito:

- ✅ **Explicável:** cada sugestão tem um motivo claro e auditável — essencial para um gestor confiar na decisão de comprar.
- ✅ **Não precisa de treinamento:** funciona desde a primeira movimentação registrada, sem precisar de uma base histórica enorme.
- ✅ **Barata e rápida:** é uma conta simples sobre dados que já estão no banco.
- ✅ **Defensável academicamente:** é uma técnica reconhecida de previsão de demanda.

A desvantagem é que ela **não captura padrões complexos** (sazonalidade, tendência de crescimento, eventos). Isso fica como evolução futura.

---

## 6. Limitações e evoluções futuras

A previsão atual é a **v1 (média móvel simples)**. Próximos passos possíveis:

- **Suavização exponencial:** dar mais peso aos dias mais recentes.
- **Sazonalidade:** reconhecer que certos produtos vendem mais em determinados dias/meses.
- **Modelos de ML/regressão:** aprender padrões a partir do histórico.
- **Alertas automáticos:** job agendado que dispara aviso (e-mail/Telegram) quando um produto entra na zona de reposição.
- **Gráficos de série temporal** no dashboard do frontend.

---

## 7. Como ver funcionando rapidamente

1. Subir banco e backend (ver [`context.md`](context.md) → "Como rodar").
2. Popular dados de exemplo:
   ```powershell
   $env:PGPASSWORD="123456"
   psql -U postgres -h localhost -d estoqueia -f scripts/seed-demo.sql
   ```
   Isso cria ~42 produtos calibrados para gerar os quatro cenários da IA.
3. Logar e consultar a previsão (exemplo no Swagger: `http://localhost:8080/swagger-ui.html`):
   - `POST /auth/login` com `admin@teste.com` / `senha123` → copiar o `accessToken`.
   - Clicar em **Authorize**, colar o token.
   - Chamar `GET /previsao/reposicao-sugerida` → retorna ~15 produtos que precisam de reposição, cada um com seu motivo.

---

*Documento gerado para fins de entendimento e apresentação do projeto. Para detalhes de implementação, consultar o código-fonte e o [`context.md`](context.md).*
