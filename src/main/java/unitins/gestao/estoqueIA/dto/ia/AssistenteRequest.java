package unitins.gestao.estoqueIA.dto.ia;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssistenteRequest(

        @NotBlank
        @Size(max = 500)
        String pergunta
) {
}
