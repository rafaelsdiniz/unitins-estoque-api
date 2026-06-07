package unitins.gestao.estoqueIA.service.ia;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import unitins.gestao.estoqueIA.dto.analise.ResumoEstoque;
import unitins.gestao.estoqueIA.dto.ia.ChatMessage;
import unitins.gestao.estoqueIA.dto.ia.MovimentacaoNlResponse;
import unitins.gestao.estoqueIA.dto.previsao.PrevisaoResponse;
import unitins.gestao.estoqueIA.dto.produto.ProdutoResponse;
import unitins.gestao.estoqueIA.entity.enums.TipoMovimentacao;
import unitins.gestao.estoqueIA.service.EstoqueAnaliseService;
import unitins.gestao.estoqueIA.service.PrevisaoService;
import unitins.gestao.estoqueIA.service.ProdutoService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agente de IA que auxilia nas decisões de reposição de estoque.
 *
 * Estratégia: os NÚMEROS continuam vindo do {@link PrevisaoService}
 * (consumo médio, dias até ruptura, etc. — lógica determinística e confiável).
 * O LLM entra apenas para INTERPRETAR esses dados, priorizar e explicar a
 * recomendação em linguagem natural. Assim a IA não "inventa" estoque:
 * ela só raciocina sobre o contexto que injetamos no prompt.
 */
@Service
@RequiredArgsConstructor
public class IaAssistenteService {

    private static final String SYSTEM_PROMPT = """
            Você é um assistente de gestão de estoque do sistema estoqueIA.
            Responda em português do Brasil, de forma objetiva e prática.
            Baseie-se SOMENTE nos dados de estoque fornecidos no contexto.
            Se a pergunta não puder ser respondida com esses dados, diga isso claramente.
            Ao recomendar reposição, priorize os itens com menor "dias até ruptura"
            e explique brevemente o motivo de cada sugestão.
            """;

    /**
     * Instrução de sistema do chat de dúvidas: explica o que é o estoqueIA e
     * como o usuário interage com ele, para responder perguntas de uso.
     * O contexto do estoque (números reais) é anexado dinamicamente.
     */
    private static final String CHAT_SYSTEM_PROMPT = """
            Você é o assistente virtual do estoqueIA, um sistema web de gestão de estoque
            com previsão de reposição. Responda em português do Brasil, de forma amigável,
            curta e prática.

            Você ajuda o usuário em dois tipos de pergunta:
            1. DÚVIDAS DE USO do sistema. Funcionalidades disponíveis:
               - Produtos: cadastro, edição, busca por nome, lista de baixo estoque (ADMIN cria/edita).
               - Categorias: organização dos produtos (ADMIN cria/edita).
               - Movimentações: registrar ENTRADA ou SAÍDA; a saída valida se há estoque suficiente.
               - Previsão/IA: o sistema calcula consumo médio e sugere reposição.
               - Perfil: cada usuário edita seus dados; papéis são USUARIO e ADMIN.
            2. PERGUNTAS SOBRE O ESTOQUE ATUAL, respondendo com base no contexto fornecido abaixo.

            Regras: baseie respostas sobre números SOMENTE no contexto fornecido; se o dado não
            estiver lá, diga que não tem essa informação. Ao recomendar reposição, priorize os
            itens com menor "dias até ruptura" e explique o motivo em uma frase.
            """;

    private static final int MAX_MENSAGENS = 30;

    private final PrevisaoService previsaoService;
    private final EstoqueAnaliseService estoqueAnaliseService;
    private final ProdutoService produtoService;
    private final DeepSeekClient deepSeekClient;
    private final ObjectMapper objectMapper;

    public String responder(String pergunta) {
        List<PrevisaoResponse> sugestoes = previsaoService.reposicaoSugerida(null);
        String contexto = montarContexto(sugestoes);

        String userPrompt = """
                ## Contexto do estoque (itens com reposição sugerida pelo sistema)
                %s

                ## Pergunta do usuário
                %s
                """.formatted(contexto, pergunta);

        return deepSeekClient.chat(SYSTEM_PROMPT, userPrompt);
    }

    /**
     * Chat multi-turno. O histórico vem do cliente (sessão do navegador).
     * Montamos: [system com contexto do estoque] + [histórico user/assistant].
     */
    public String responderChat(List<ChatMessage> historico) {
        String contexto = montarContexto(previsaoService.reposicaoSugerida(null));

        String system = CHAT_SYSTEM_PROMPT + """

                ## Contexto do estoque agora (itens com reposição sugerida pelo sistema)
                %s
                """.formatted(contexto);

        List<DeepSeekClient.Message> mensagens = new ArrayList<>();
        mensagens.add(new DeepSeekClient.Message("system", system));

        // mantém apenas as últimas N mensagens para limitar o custo
        List<ChatMessage> recentes = historico.size() > MAX_MENSAGENS
                ? historico.subList(historico.size() - MAX_MENSAGENS, historico.size())
                : historico;
        for (ChatMessage m : recentes) {
            mensagens.add(new DeepSeekClient.Message(m.papel(), m.conteudo()));
        }

        return deepSeekClient.chat(mensagens);
    }

    /**
     * Resumo executivo do estoque em linguagem natural, para o dashboard.
     * Os números vêm do {@link EstoqueAnaliseService}; o LLM apenas redige.
     */
    public String resumoExecutivo() {
        ResumoEstoque r = estoqueAnaliseService.resumo();
        String topReposicao = montarContexto(previsaoService.reposicaoSugerida(null));

        String userPrompt = """
                Escreva um resumo executivo curto (3 a 5 frases) sobre a situação atual do estoque,
                em tom profissional, destacando riscos e prioridades. Não invente números.

                ## Indicadores
                - Produtos ativos: %d
                - Categorias: %d
                - Unidades em estoque: %d
                - Valor imobilizado: R$ %s
                - Produtos abaixo do mínimo: %d
                - Produtos com reposição sugerida: %d
                - Categoria mais crítica: %s

                ## Itens prioritários para reposição
                %s
                """.formatted(
                r.totalProdutosAtivos(),
                r.totalCategorias(),
                r.totalUnidades(),
                r.valorTotalEstoque(),
                r.produtosAbaixoMinimo(),
                r.produtosComReposicaoSugerida(),
                r.categoriaMaisCritica() == null ? "n/d" : r.categoriaMaisCritica(),
                topReposicao
        );

        return deepSeekClient.chat(SYSTEM_PROMPT, userPrompt);
    }

    /**
     * Gera um rascunho de pedido de compra a partir dos itens com reposição
     * sugerida e suas quantidades calculadas. É só uma sugestão para o ADMIN.
     */
    public String gerarPedidoCompra() {
        List<PrevisaoResponse> sugestoes = previsaoService.reposicaoSugerida(null);
        if (sugestoes.isEmpty()) {
            return "Nenhum produto precisa de reposição no momento. Não há pedido de compra a gerar.";
        }

        String itens = sugestoes.stream()
                .map(p -> "- %s | estoque atual: %d | mínimo: %d | quantidade sugerida de compra: %s | motivo: %s"
                        .formatted(
                                p.produtoNome(),
                                p.quantidadeAtual(),
                                p.estoqueMinimo(),
                                p.quantidadeSugerida() == null ? "n/d" : p.quantidadeSugerida(),
                                p.motivo()))
                .collect(Collectors.joining("\n"));

        String userPrompt = """
                Monte um rascunho de PEDIDO DE COMPRA com base na lista abaixo. Para cada item,
                liste o produto e a quantidade sugerida de compra. Ordene pelos mais urgentes
                (menor folga / abaixo do mínimo primeiro). Ao final, escreva uma linha de
                observação lembrando que é uma sugestão a ser revisada pelo responsável.
                Use as quantidades fornecidas; não invente novos números.

                ## Itens sugeridos
                %s
                """.formatted(itens);

        return deepSeekClient.chat(SYSTEM_PROMPT, userPrompt);
    }

    /**
     * Interpreta uma frase como "dei baixa de 10 parafusos" e devolve uma
     * pré-visualização estruturada (tipo, quantidade, produto). NÃO grava nada:
     * o cliente confirma e chama POST /movimentacoes.
     */
    public MovimentacaoNlResponse interpretarMovimentacao(String texto) {
        String system = """
                Extraia de uma frase a intenção de movimentação de estoque e responda SOMENTE com
                um JSON válido, sem texto extra, sem markdown, no formato exato:
                {"tipo":"ENTRADA"|"SAIDA","quantidade":<inteiro>,"produto":"<nome do produto>"}
                "ENTRADA" = chegada/compra/reposição; "SAIDA" = baixa/venda/consumo.
                Se não conseguir identificar, responda {"tipo":null,"quantidade":null,"produto":null}.
                """;

        String resposta = deepSeekClient.chat(system, texto);
        JsonNode json;
        try {
            json = objectMapper.readTree(limparJson(resposta));
        } catch (Exception e) {
            return new MovimentacaoNlResponse(false, null, null, null, null,
                    "Não consegui interpretar a frase. Reformule, ex.: \"saída de 10 unidades de Mouse\".");
        }

        String tipoTxt = textoOuNull(json, "tipo");
        Integer quantidade = inteiroOuNull(json, "quantidade");
        String produtoBusca = textoOuNull(json, "produto");

        if (tipoTxt == null || quantidade == null || quantidade <= 0 || produtoBusca == null) {
            return new MovimentacaoNlResponse(false, null, null, null, null,
                    "Não consegui identificar tipo, quantidade e produto na frase.");
        }

        TipoMovimentacao tipo;
        try {
            tipo = TipoMovimentacao.valueOf(tipoTxt.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return new MovimentacaoNlResponse(false, null, null, null, null,
                    "Tipo de movimentação inválido: " + tipoTxt);
        }

        List<ProdutoResponse> encontrados = produtoService
                .listar(produtoBusca, PageRequest.of(0, 5)).getContent();
        if (encontrados.isEmpty()) {
            return new MovimentacaoNlResponse(false, null, null, tipo, quantidade,
                    "Não encontrei nenhum produto parecido com \"" + produtoBusca + "\".");
        }

        ProdutoResponse p = encontrados.get(0);
        String aviso = encontrados.size() > 1
                ? " (havia mais de um produto parecido; confirme se é o correto)"
                : "";
        return new MovimentacaoNlResponse(true, p.id(), p.nome(), tipo, quantidade,
                "Confirme para registrar %s de %d unidade(s) de \"%s\".%s"
                        .formatted(tipo, quantidade, p.nome(), aviso));
    }

    private String limparJson(String texto) {
        if (texto == null) return "{}";
        String t = texto.trim();
        // remove cercas de código ```json ... ```
        if (t.startsWith("```")) {
            int ini = t.indexOf('{');
            int fim = t.lastIndexOf('}');
            if (ini >= 0 && fim >= ini) {
                return t.substring(ini, fim + 1);
            }
        }
        return t;
    }

    private String textoOuNull(JsonNode node, String campo) {
        JsonNode n = node.get(campo);
        return n != null && !n.isNull() ? n.asString() : null;
    }

    private Integer inteiroOuNull(JsonNode node, String campo) {
        JsonNode n = node.get(campo);
        return n != null && n.isNumber() ? n.asInt() : null;
    }

    private String montarContexto(List<PrevisaoResponse> sugestoes) {
        if (sugestoes.isEmpty()) {
            return "Nenhum produto precisa de reposição no momento.";
        }
        return sugestoes.stream()
                .map(p -> "- %s | estoque atual: %d | mínimo: %d | consumo médio/dia: %s | dias até ruptura: %s | tempo de reposição: %s dia(s) | motivo: %s"
                        .formatted(
                                p.produtoNome(),
                                p.quantidadeAtual(),
                                p.estoqueMinimo(),
                                p.consumoMedioDiario(),
                                p.diasAteRuptura() == null ? "n/d" : p.diasAteRuptura(),
                                p.tempoReposicaoDias() == null ? "n/d" : p.tempoReposicaoDias(),
                                p.motivo()
                        ))
                .collect(Collectors.joining("\n"));
    }
}
