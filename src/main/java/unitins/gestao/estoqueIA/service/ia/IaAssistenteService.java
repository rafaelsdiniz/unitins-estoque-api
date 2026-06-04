package unitins.gestao.estoqueIA.service.ia;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import unitins.gestao.estoqueIA.dto.ia.ChatMessage;
import unitins.gestao.estoqueIA.dto.previsao.PrevisaoResponse;
import unitins.gestao.estoqueIA.service.PrevisaoService;

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
    private final DeepSeekClient deepSeekClient;

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
