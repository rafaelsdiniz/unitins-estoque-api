package unitins.gestao.estoqueIA.dto.produto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProdutoRequest(

        @NotBlank
        @Size(max = 150)
        String nome,

        @Size(max = 500)
        String descricao,

        @NotNull
        @DecimalMin(value = "0.00", inclusive = true)
        BigDecimal precoUnitario,

        @PositiveOrZero
        Integer estoqueMinimo,

        @PositiveOrZero
        Integer tempoReposicaoDias,

        Long categoriaId
) {
}
