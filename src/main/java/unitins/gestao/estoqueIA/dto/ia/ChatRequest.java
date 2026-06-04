package unitins.gestao.estoqueIA.dto.ia;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Histórico completo da conversa. A última mensagem deve ser do usuário.
 * Limitado a 30 mensagens para controlar o custo em tokens.
 */
public record ChatRequest(

        @NotEmpty
        @Size(max = 30, message = "histórico muito longo (máx. 30 mensagens)")
        @Valid
        List<ChatMessage> mensagens
) {
}
