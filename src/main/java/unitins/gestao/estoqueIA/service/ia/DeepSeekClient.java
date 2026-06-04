package unitins.gestao.estoqueIA.service.ia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
 * sem necessidade de SDK externo.
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
        if (!apiKeyConfigurada) {
            throw new BusinessException(
                    "IA indisponível: defina a variável de ambiente DEEPSEEK_API_KEY antes de iniciar o backend."
            );
        }

        ChatRequest body = new ChatRequest(model, messages, false, 0.3);

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
        return resp.choices().get(0).message().content();
    }

    // ===== DTOs do protocolo (compatível com OpenAI) =====

    record ChatRequest(String model, List<Message> messages, boolean stream, double temperature) {
    }

    record Message(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(List<Choice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message) {
    }
}
