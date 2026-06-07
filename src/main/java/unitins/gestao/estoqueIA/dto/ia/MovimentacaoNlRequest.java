package unitins.gestao.estoqueIA.dto.ia;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Texto em linguagem natural descrevendo uma movimentação, ex.:
 * "dei baixa de 10 parafusos" ou "entraram 50 mouses".
 */
public record MovimentacaoNlRequest(

        @NotBlank
        @Size(max = 300)
        String texto
) {
}
