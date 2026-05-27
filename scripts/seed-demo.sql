-- ============================================================
-- estoqueIA — Seed de demonstração
-- ============================================================
-- Popula categorias, produtos e movimentações (entradas + saídas)
-- com datas espalhadas nos últimos ~40 dias, para exercitar a
-- previsão por média móvel (PrevisaoService).
--
-- Os campos quantidade / estoque_minimo / tempo_reposicao_dias e o
-- consumo diário das saídas são calibrados para gerar 4 cenários:
--   * ABAIXO DO MÍNIMO  -> sugere reposição
--   * RUPTURA PRÓXIMA    -> sugere reposição
--   * ESTOQUE ADEQUADO   -> não sugere
--   * CONSUMO ZERO       -> não sugere ("Sem saídas na janela")
--
-- Idempotente por NOME de produto: rodar de novo não duplica.
-- Uso:
--   $env:PGPASSWORD="123456"
--   psql -U postgres -h localhost -d estoqueia -f scripts/seed-demo.sql
-- ============================================================

DO $$
DECLARE
    v_admin_id BIGINT;
    v_user_id  BIGINT;
    rec        RECORD;
    v_prod_id  BIGINT;
    v_cat_id   BIGINT;
    d          INT;
    qtd        INT;
BEGIN
    SELECT id INTO v_admin_id FROM usuario WHERE email = 'admin@teste.com' LIMIT 1;
    SELECT id INTO v_user_id  FROM usuario WHERE email = 'user@teste.com'  LIMIT 1;
    -- fallback: qualquer usuário, se os de exemplo não existirem
    IF v_admin_id IS NULL THEN SELECT id INTO v_admin_id FROM usuario ORDER BY id LIMIT 1; END IF;
    IF v_user_id  IS NULL THEN v_user_id := v_admin_id; END IF;

    -- ---------- Categorias (não duplica: nome é UNIQUE) ----------
    INSERT INTO categoria (nome, descricao) VALUES
        ('Alimentos',   'Enlatados, biscoitos e conservas'),
        ('Mercearia',   'Grãos, óleos e itens secos'),
        ('Limpeza',     'Produtos de limpeza doméstica'),
        ('Higiene',     'Higiene pessoal'),
        ('Hortifruti',  'Frutas, legumes e verduras'),
        ('Padaria',     'Pães e bolos'),
        ('Laticínios',  'Leite e derivados')
    ON CONFLICT (nome) DO NOTHING;
    -- 'Bebidas' já existe no banco; garantimos caso não exista
    INSERT INTO categoria (nome, descricao) VALUES ('Bebidas', 'Bebidas em geral')
    ON CONFLICT (nome) DO NOTHING;

    -- ---------- Produtos + Movimentações ----------
    -- colunas: nome, categoria, preco, quantidade, estoque_minimo, tempo_reposicao_dias, consumo_dia
    FOR rec IN
        SELECT * FROM (VALUES
            -- ===== BEBIDAS =====
            ('Suco de Laranja 1L',        'Bebidas',     8.50,  18, 25, 5,  4),  -- ABAIXO MIN
            ('Água Mineral 500ml',        'Bebidas',     2.00, 600,100, 4, 20),  -- adequado
            ('Cerveja Lata 350ml',        'Bebidas',     3.80,  90, 50, 7, 12),  -- RUPTURA
            ('Refrigerante Cola 2L',      'Bebidas',     9.90, 200, 40, 5,  5),  -- adequado
            ('Energético 250ml',          'Bebidas',     7.50,  35, 30, 6,  0),  -- CONSUMO ZERO
            -- ===== MERCEARIA =====
            ('Arroz Branco 5kg',          'Mercearia',  28.90,  35, 30, 7,  6),  -- RUPTURA
            ('Feijão Carioca 1kg',        'Mercearia',   8.49,  12, 25, 5,  5),  -- ABAIXO MIN
            ('Macarrão Espaguete 500g',   'Mercearia',   4.20, 300, 50, 4,  8),  -- adequado
            ('Óleo de Soja 900ml',        'Mercearia',   7.80,  80, 40, 6,  4),  -- adequado
            ('Açúcar Refinado 1kg',       'Mercearia',   4.99, 150, 40, 5,  7),  -- adequado
            ('Café Torrado 500g',         'Mercearia',  15.90,  45, 30,10,  5),  -- RUPTURA
            ('Sal Refinado 1kg',          'Mercearia',   2.50, 250, 30, 7,  2),  -- adequado
            ('Farinha de Trigo 1kg',      'Mercearia',   5.30,  60, 30, 5,  3),  -- adequado
            ('Molho de Tomate 340g',      'Mercearia',   3.10,  22, 40, 4,  6),  -- ABAIXO MIN
            -- ===== ALIMENTOS =====
            ('Biscoito Recheado 130g',    'Alimentos',   2.80, 400, 60, 4, 15),  -- adequado
            ('Atum em Lata 170g',         'Alimentos',   9.20,  50, 20, 6,  0),  -- CONSUMO ZERO
            ('Leite Condensado 395g',     'Alimentos',   6.50, 120, 30, 5,  4),  -- adequado
            -- ===== LIMPEZA =====
            ('Detergente Neutro 500ml',   'Limpeza',     2.90, 130, 40, 5,  6),  -- adequado
            ('Sabão em Pó 1kg',           'Limpeza',    12.90,  28, 35, 7,  4),  -- ABAIXO MIN
            ('Água Sanitária 1L',         'Limpeza',     4.50,  90, 30, 6,  9),  -- adequado
            ('Amaciante 2L',              'Limpeza',    11.90,  40, 25, 8,  6),  -- RUPTURA
            ('Desinfetante 1L',           'Limpeza',     6.30, 110, 30, 5,  5),  -- adequado
            ('Esponja Multiuso',          'Limpeza',     1.90, 500, 80, 4, 10),  -- adequado
            -- ===== HIGIENE =====
            ('Sabonete 90g',              'Higiene',     2.20, 350, 60, 5,  8),  -- adequado
            ('Shampoo 350ml',             'Higiene',    14.90,  32, 20, 9,  4),  -- RUPTURA
            ('Creme Dental 90g',          'Higiene',     5.50, 140, 40, 6,  5),  -- adequado
            ('Papel Higiênico 12 rolos',  'Higiene',    18.90,  15, 30, 7,  3),  -- ABAIXO MIN
            ('Desodorante Aerosol',       'Higiene',    13.50,  70, 25, 7,  0),  -- CONSUMO ZERO
            -- ===== HORTIFRUTI =====
            ('Banana Prata kg',           'Hortifruti',  5.99,  40, 30, 2, 18),  -- RUPTURA
            ('Tomate kg',                 'Hortifruti',  7.49,  50, 25, 2, 14),  -- adequado
            ('Batata kg',                 'Hortifruti',  4.99, 120, 40, 3, 16),  -- adequado
            ('Cebola kg',                 'Hortifruti',  3.99,  80, 30, 3, 12),  -- adequado
            ('Maçã kg',                   'Hortifruti',  9.90,  22, 30, 2, 10),  -- ABAIXO MIN
            -- ===== PADARIA =====
            ('Pão Francês kg',            'Padaria',    14.90,  25, 20, 1, 22),  -- RUPTURA
            ('Bolo de Fubá un',           'Padaria',    12.00,  30, 15, 2,  5),  -- adequado
            ('Pão de Forma 500g',         'Padaria',     8.90,  60, 25, 3,  9),  -- adequado
            -- ===== LATICÍNIOS =====
            ('Leite Integral 1L',         'Laticínios',  5.49,  70, 50, 3, 20),  -- RUPTURA
            ('Queijo Mussarela kg',       'Laticínios', 39.90,  18, 20, 4,  4),  -- ABAIXO MIN
            ('Manteiga 200g',             'Laticínios',  9.90,  90, 30, 5,  6),  -- adequado
            ('Iogurte Natural 170g',      'Laticínios',  3.50, 110, 40, 3, 14),  -- adequado
            ('Requeijão 200g',            'Laticínios',  7.20,  50, 20, 4,  0)   -- CONSUMO ZERO
        ) AS t(nome, categoria, preco, quantidade, estoque_minimo, tempo_rep, consumo_dia)
    LOOP
        -- idempotência: pula produto já existente
        IF EXISTS (SELECT 1 FROM produto WHERE nome = rec.nome) THEN
            CONTINUE;
        END IF;

        SELECT id INTO v_cat_id FROM categoria WHERE nome = rec.categoria LIMIT 1;

        INSERT INTO produto (codigo, nome, descricao, preco_unitario, quantidade,
                             estoque_minimo, tempo_reposicao_dias, categoria_id, ativo,
                             data_criacao, data_atualizacao)
        VALUES ('PROD-' || upper(substr(md5(random()::text || rec.nome), 1, 8)),
                rec.nome, rec.nome, rec.preco, rec.quantidade,
                rec.estoque_minimo, rec.tempo_rep, v_cat_id, TRUE,
                CURRENT_TIMESTAMP - make_interval(days => 60),
                CURRENT_TIMESTAMP)
        RETURNING id INTO v_prod_id;

        -- ENTRADAS: reabastecimentos pontuais nos últimos 48 dias
        INSERT INTO movimentacao (produto_id, tipo, quantidade, preco_unitario_epoca,
                                  data_hora, usuario_id, observacao)
        SELECT v_prod_id, 'ENTRADA',
               GREATEST(30, rec.consumo_dia * 12 + 20),
               round((rec.preco * 0.6)::numeric, 2),
               CURRENT_TIMESTAMP - make_interval(days => g, hours => 8),
               v_admin_id, 'Reabastecimento de estoque'
        FROM generate_series(48, 12, -12) AS g;

        -- SAÍDAS: vendas diárias nos últimos 40 dias (só se há consumo)
        IF rec.consumo_dia > 0 THEN
            FOR d IN 1..40 LOOP
                qtd := rec.consumo_dia + (floor(random() * 3)::int - 1);  -- ruído -1..+1
                IF qtd > 0 THEN
                    INSERT INTO movimentacao (produto_id, tipo, quantidade,
                                              preco_unitario_epoca, data_hora,
                                              usuario_id, observacao)
                    VALUES (v_prod_id, 'SAIDA', qtd, rec.preco,
                            CURRENT_TIMESTAMP
                              - make_interval(days => d)
                              - make_interval(hours => floor(random() * 11)::int),
                            v_user_id, 'Venda balcão');
                END IF;
            END LOOP;
        END IF;
    END LOOP;

    -- Soft-delete de demonstração: 2 produtos descontinuados
    UPDATE produto SET ativo = FALSE
     WHERE nome IN ('Bolo de Fubá un', 'Requeijão 200g');

    RAISE NOTICE 'Seed concluído.';
END $$;

-- Resumo do que ficou no banco
SELECT 'categorias' AS entidade, count(*) FROM categoria
UNION ALL SELECT 'produtos',      count(*) FROM produto
UNION ALL SELECT 'movimentacoes', count(*) FROM movimentacao;
