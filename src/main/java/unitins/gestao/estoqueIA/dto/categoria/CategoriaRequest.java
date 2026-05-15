package unitins.gestao.estoqueIA.dto.categoria;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoriaRequest(

        @NotBlank
        @Size(max = 100)
        String nome,

        @Size(max = 255)
        String descricao
) {
}
