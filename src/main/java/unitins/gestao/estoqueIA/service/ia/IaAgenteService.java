package unitins.gestao.estoqueIA.service.ia;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import unitins.gestao.estoqueIA.dto.ia.ChatMessage;
import unitins.gestao.estoqueIA.exception.BusinessException;
import unitins.gestao.estoqueIA.service.EstoqueAnaliseService;
import unitins.gestao.estoqueIA.service.MovimentacaoService;
import unitins.gestao.estoqueIA.service.PrevisaoService;
import unitins.gestao.estoqueIA.service.ProdutoService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agente de IA — o "centro" do estoqueIA.
 *
 * Diferente do chat simples (que recebe um contexto fixo no prompt), o agente
 * pode CHAMAR FERRAMENTAS do sistema sob demanda (function calling): buscar um
 * produto, calcular previsão, ver o resumo, a curva ABC, anomalias, histórico.
 * O modelo decide quais funções chamar; nós executamos os serviços reais
 * (números determinísticos) e devolvemos os resultados para ele concluir.
 *
 * Assim a IA responde perguntas abertas ("quanto tenho do produto X e quando
 * vou precisar repor?") consultando o sistema, sem nunca inventar dados.
 */
@Service
@RequiredArgsConstructor
public class IaAgenteService {

    private static final String SYSTEM_PROMPT = """
            Você é o agente inteligente do estoqueIA, um sistema de gestão de estoque.
            Responda em português do Brasil, de forma objetiva, prática e amigável.

            Você tem FERRAMENTAS para consultar dados reais do estoque. Use-as sempre que
            a pergunta exigir números (quantidades, previsões, valores, riscos). NUNCA invente
            dados: se precisar de um número, chame a ferramenta apropriada. Se a informação não
            estiver disponível mesmo após consultar, diga isso com clareza.

            Ao recomendar reposição, priorize os itens com menor "dias até ruptura" e explique
            o motivo em uma frase. Quando citar valores monetários, use o formato R$.
            """;

    /** Limite de rodadas de chamada de ferramenta, para evitar loop infinito. */
    private static final int MAX_ITERACOES = 5;
    private static final int MAX_MENSAGENS = 20;

    private final DeepSeekClient deepSeekClient;
    private final ObjectMapper objectMapper;
    private final ProdutoService produtoService;
    private final PrevisaoService previsaoService;
    private final EstoqueAnaliseService estoqueAnaliseService;
    private final MovimentacaoService movimentacaoService;

    public String conversar(List<ChatMessage> historico) {
        List<DeepSeekClient.Message> mensagens = new ArrayList<>();
        mensagens.add(new DeepSeekClient.Message("system", SYSTEM_PROMPT));

        List<ChatMessage> recentes = historico.size() > MAX_MENSAGENS
                ? historico.subList(historico.size() - MAX_MENSAGENS, historico.size())
                : historico;
        for (ChatMessage m : recentes) {
            mensagens.add(new DeepSeekClient.Message(m.papel(), m.conteudo()));
        }

        List<DeepSeekClient.Tool> ferramentas = ferramentas();

        for (int i = 0; i < MAX_ITERACOES; i++) {
            DeepSeekClient.Message resposta = deepSeekClient.chatComFerramentas(mensagens, ferramentas);
            mensagens.add(resposta);

            List<DeepSeekClient.ToolCall> chamadas = resposta.toolCalls();
            if (chamadas == null || chamadas.isEmpty()) {
                return resposta.content();
            }

            for (DeepSeekClient.ToolCall chamada : chamadas) {
                String resultado = executar(
                        chamada.function().name(),
                        chamada.function().arguments()
                );
                mensagens.add(DeepSeekClient.Message.ferramenta(
                        chamada.id(), chamada.function().name(), resultado));
            }
        }

        // Esgotou as iterações: pede uma resposta final sem novas ferramentas.
        return deepSeekClient.chat(mensagens);
    }

    // ===== Definição das ferramentas expostas ao modelo =====

    private List<DeepSeekClient.Tool> ferramentas() {
        Map<String, Object> semParametros = Map.of("type", "object", "properties", Map.of());

        Map<String, Object> janelaOpcional = Map.of(
                "type", "object",
                "properties", Map.of(
                        "janelaDias", Map.of("type", "integer",
                                "description", "Janela em dias para o cálculo (padrão 30)")
                )
        );

        return List.of(
                DeepSeekClient.Tool.funcao("resumo_estoque",
                        "Visão geral do estoque: total de produtos, unidades, valor imobilizado, "
                                + "quantos estão abaixo do mínimo e a categoria mais crítica.",
                        semParametros),

                DeepSeekClient.Tool.funcao("reposicao_sugerida",
                        "Lista os produtos cuja reposição é recomendada agora, com dias até ruptura "
                                + "e quantidade sugerida de compra.",
                        janelaOpcional),

                DeepSeekClient.Tool.funcao("prever_produto",
                        "Previsão de consumo e reposição de UM produto específico pelo seu ID.",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "produtoId", Map.of("type", "integer", "description", "ID do produto"),
                                        "janelaDias", Map.of("type", "integer", "description", "Janela em dias (padrão 30)")
                                ),
                                "required", List.of("produtoId"))),

                DeepSeekClient.Tool.funcao("buscar_produto",
                        "Busca produtos pelo nome (parcial). Use para descobrir o ID de um produto "
                                + "antes de prever ou ver histórico.",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "nome", Map.of("type", "string", "description", "Parte do nome do produto")
                                ),
                                "required", List.of("nome"))),

                DeepSeekClient.Tool.funcao("curva_abc",
                        "Curva ABC dos produtos por valor imobilizado em estoque (classes A, B, C).",
                        semParametros),

                DeepSeekClient.Tool.funcao("anomalias",
                        "Anomalias de consumo: picos de saída e estoque parado.",
                        janelaOpcional),

                DeepSeekClient.Tool.funcao("historico_movimentacao",
                        "Últimas movimentações (entradas/saídas) de um produto pelo seu ID.",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "produtoId", Map.of("type", "integer", "description", "ID do produto")
                                ),
                                "required", List.of("produtoId")))
        );
    }

    // ===== Execução das ferramentas (serviços reais) =====

    private String executar(String nome, String argumentosJson) {
        try {
            JsonNode args = lerArgumentos(argumentosJson);
            Object resultado = switch (nome) {
                case "resumo_estoque" -> estoqueAnaliseService.resumo();
                case "reposicao_sugerida" -> previsaoService.reposicaoSugerida(inteiro(args, "janelaDias"));
                case "prever_produto" -> previsaoService.prever(
                        obrigatorioLong(args, "produtoId"), inteiro(args, "janelaDias"));
                case "buscar_produto" -> produtoService
                        .listar(texto(args, "nome"), PageRequest.of(0, 10)).getContent();
                case "curva_abc" -> estoqueAnaliseService.curvaAbc();
                case "anomalias" -> estoqueAnaliseService.anomalias(inteiro(args, "janelaDias"));
                case "historico_movimentacao" -> movimentacaoService
                        .listarPorProduto(obrigatorioLong(args, "produtoId"), PageRequest.of(0, 10)).getContent();
                default -> Map.of("erro", "Ferramenta desconhecida: " + nome);
            };
            return objectMapper.writeValueAsString(resultado);
        } catch (BusinessException e) {
            return erroJson(e.getMessage());
        } catch (Exception e) {
            return erroJson("Falha ao executar a ferramenta '" + nome + "': " + e.getMessage());
        }
    }

    private JsonNode lerArgumentos(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(json);
    }

    private Integer inteiro(JsonNode args, String campo) {
        JsonNode n = args.get(campo);
        return n != null && n.isNumber() ? n.asInt() : null;
    }

    private Long obrigatorioLong(JsonNode args, String campo) {
        JsonNode n = args.get(campo);
        if (n == null || !n.isNumber()) {
            throw new BusinessException("Argumento obrigatório ausente: " + campo);
        }
        return n.asLong();
    }

    private String texto(JsonNode args, String campo) {
        JsonNode n = args.get(campo);
        return n != null && !n.isNull() ? n.asString() : "";
    }

    private String erroJson(String mensagem) {
        try {
            return objectMapper.writeValueAsString(Map.of("erro", mensagem));
        } catch (Exception e) {
            return "{\"erro\":\"erro ao serializar\"}";
        }
    }
}
