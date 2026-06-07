package unitins.gestao.estoqueIA.service.ia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import unitins.gestao.estoqueIA.exception.BusinessException;

import java.util.List;

/**
 * Cliente HTTP para a API da DeepSeek.
 *
 * A DeepSeek expõe um endpoint compatível com o formato da OpenAI
 * (POST /chat/completions), então usamos o {@link RestClient} do Spring —
 * sem necessidade de SDK externo. Suporta também <b>function calling</b>
 * (ferramentas): o modelo pode pedir para chamar funções do nosso sistema,
 * nós executamos e devolvemos o resultado para ele raciocinar.
 *
 * Configuração (application.properties / variáveis de ambiente):
 *   deepseek.api-key   -> DEEPSEEK_API_KEY  (obrigatório; nunca commitar)
 *   deepseek.model     -> deepseek-chat por padrão
 *   deepseek.base-url  -> https://api.deepseek.com por padrão
 */
@Component
public class DeepSeekClient {

    private final RestClient restClient;
    private final String model;
    private final boolean apiKeyConfigurada;

    public DeepSeekClient(
            @Value("${deepseek.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${deepseek.api-key:}") String apiKey,
            @Value("${deepseek.model:deepseek-chat}") String model
    ) {
        this.model = model;
        this.apiKeyConfigurada = apiKey != null && !apiKey.isBlank();
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    /**
     * Envia um par (instrução de sistema, pergunta do usuário) e devolve o texto da resposta.
     * Atalho para uma conversa de turno único.
     */
    public String chat(String systemPrompt, String userPrompt) {
        return chat(List.of(
                new Message("system", systemPrompt),
                new Message("user", userPrompt)
        ));
    }

    /**
     * Envia uma conversa completa (várias mensagens com papéis system/user/assistant)
     * e devolve o texto da resposta. Usado pelo chat multi-turno.
     */
    public String chat(List<Message> messages) {
        return enviar(messages, null).content();
    }

    /**
     * Envia a conversa disponibilizando ferramentas ao modelo. Retorna a mensagem
     * do assistente, que pode conter texto OU pedidos de chamada de ferramenta
     * ({@code tool_calls}). O loop de execução fica no {@code IaAgenteService}.
     */
    public Message chatComFerramentas(List<Message> messages, List<Tool> tools) {
        return enviar(messages, tools);
    }

    private Message enviar(List<Message> messages, List<Tool> tools) {
        if (!apiKeyConfigurada) {
            throw new BusinessException(
                    "IA indisponível: defina a variável de ambiente DEEPSEEK_API_KEY antes de iniciar o backend."
            );
        }

        ChatRequest body = new ChatRequest(model, messages, false, 0.3, tools);

        ChatResponse resp;
        try {
            resp = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (RuntimeException ex) {
            throw new BusinessException("Falha ao consultar a DeepSeek: " + ex.getMessage());
        }

        if (resp == null || resp.choices() == null || resp.choices().isEmpty()) {
            throw new BusinessException("A DeepSeek retornou uma resposta vazia.");
        }
        return resp.choices().get(0).message();
    }

    // ===== DTOs do protocolo (compatível com OpenAI) =====

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ChatRequest(String model, List<Message> messages, boolean stream, double temperature,
                       List<Tool> tools) {
    }

    /**
     * Mensagem da conversa. Campos opcionais (tool_calls / tool_call_id / name)
     * só aparecem no JSON quando preenchidos (graças ao NON_NULL), mantendo o
     * payload limpo para mensagens simples de system/user/assistant.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            String role,
            String content,
            @JsonProperty("tool_calls") List<ToolCall> toolCalls,
            @JsonProperty("tool_call_id") String toolCallId,
            String name
    ) {
        /** Mensagem simples (system/user/assistant) sem ferramentas. */
        public Message(String role, String content) {
            this(role, content, null, null, null);
        }

        /** Resultado de uma ferramenta executada (papel "tool"). */
        public static Message ferramenta(String toolCallId, String nome, String conteudo) {
            return new Message("tool", conteudo, null, toolCallId, nome);
        }
    }

    // ----- Definição de ferramentas (enviadas no request) -----

    public record Tool(String type, ToolFunction function) {
        public static Tool funcao(String nome, String descricao, Object parametros) {
            return new Tool("function", new ToolFunction(nome, descricao, parametros));
        }
    }

    /** parameters é um JSON Schema (Map) descrevendo os argumentos da função. */
    public record ToolFunction(String name, String description, Object parameters) {
    }

    // ----- Pedido de chamada de ferramenta (vem na resposta) -----

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ToolCall(String id, String type, FunctionCall function) {
    }

    /** arguments é uma string JSON com os valores escolhidos pelo modelo. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FunctionCall(String name, String arguments) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(List<Choice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message) {
    }
}
