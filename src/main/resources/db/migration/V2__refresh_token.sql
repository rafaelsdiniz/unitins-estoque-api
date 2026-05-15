-- ============================================
-- V2: Refresh token
-- ============================================

CREATE TABLE refresh_token (
    id            BIGSERIAL    PRIMARY KEY,
    token         VARCHAR(128) NOT NULL UNIQUE,
    usuario_id    BIGINT       NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    expira_em     TIMESTAMP    NOT NULL,
    revogado      BOOLEAN      NOT NULL DEFAULT FALSE,
    data_criacao  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_refresh_token_usuario ON refresh_token (usuario_id);
CREATE INDEX idx_refresh_token_token   ON refresh_token (token);
