package unitins.gestao.estoqueIA.dto.movimentacao;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import unitins.gestao.estoqueIA.entity.enums.TipoMovimentacao;

public record MovimentacaoRequest(

        @NotNull
        Long produtoId,

        @NotNull
        TipoMovimentacao tipo,

        @NotNull
        @Positive
        Integer quantidade,

        @Size(max = 255)
        String observacao
) {
}
