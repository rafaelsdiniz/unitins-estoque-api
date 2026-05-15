-- ============================================
-- V1: Schema inicial do estoqueIA
-- ============================================

CREATE TABLE usuario (
    id            BIGSERIAL PRIMARY KEY,
    nome          VARCHAR(120) NOT NULL,
    email         VARCHAR(150) NOT NULL UNIQUE,
    senha_hash    VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    ativo         BOOLEAN      NOT NULL DEFAULT TRUE,
    data_criacao  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE categoria (
    id        BIGSERIAL PRIMARY KEY,
    nome      VARCHAR(100) NOT NULL UNIQUE,
    descricao VARCHAR(255)
);

CREATE TABLE produto (
    id                    BIGSERIAL    PRIMARY KEY,
    codigo                VARCHAR(20)  NOT NULL UNIQUE,
    nome                  VARCHAR(150) NOT NULL,
    descricao             VARCHAR(500),
    preco_unitario        NUMERIC(12,2) NOT NULL,
    quantidade            INTEGER      NOT NULL DEFAULT 0,
    estoque_minimo        INTEGER      NOT NULL DEFAULT 0,
    tempo_reposicao_dias  INTEGER,
    categoria_id          BIGINT       REFERENCES categoria(id),
    ativo                 BOOLEAN      NOT NULL DEFAULT TRUE,
    data_criacao          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_produto_nome ON produto (nome);
CREATE INDEX idx_produto_ativo ON produto (ativo);

CREATE TABLE movimentacao (
    id                     BIGSERIAL    PRIMARY KEY,
    produto_id             BIGINT       NOT NULL REFERENCES produto(id),
    tipo                   VARCHAR(10)  NOT NULL,
    quantidade             INTEGER      NOT NULL,
    preco_unitario_epoca   NUMERIC(12,2),
    data_hora              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    usuario_id             BIGINT       REFERENCES usuario(id),
    observacao             VARCHAR(255)
);

CREATE INDEX idx_movimentacao_produto_data ON movimentacao (produto_id, data_hora DESC);
