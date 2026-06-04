package unitins.gestao.estoqueIA.dto.ia;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Uma mensagem do histórico de chat enviada pelo cliente.
 * O histórico vive na sessão do navegador (não é persistido no banco).
 */
public record ChatMessage(

        @NotBlank
        @Pattern(regexp = "user|assistant", message = "papel deve ser 'user' ou 'assistant'")
        String papel,

        @NotBlank
        @Size(max = 2000)
        String conteudo
) {
}
