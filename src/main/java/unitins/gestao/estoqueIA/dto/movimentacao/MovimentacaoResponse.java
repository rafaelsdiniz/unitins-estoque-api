package unitins.gestao.estoqueIA.dto.movimentacao;

import unitins.gestao.estoqueIA.entity.Movimentacao;
import unitins.gestao.estoqueIA.entity.enums.TipoMovimentacao;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MovimentacaoResponse(
        Long id,
        Long produtoId,
        String produtoNome,
        TipoMovimentacao tipo,
        Integer quantidade,
        BigDecimal precoUnitarioNaEpoca,
        LocalDateTime dataHora,
        Long usuarioId,
        String usuarioNome,
        String observacao
) {
    public static MovimentacaoResponse from(Movimentacao m) {
        return new MovimentacaoResponse(
                m.getId(),
                m.getProduto().getId(),
                m.getProduto().getNome(),
                m.getTipo(),
                m.getQuantidade(),
                m.getPrecoUnitarioNaEpoca(),
                m.getDataHora(),
                m.getUsuario() != null ? m.getUsuario().getId() : null,
                m.getUsuario() != null ? m.getUsuario().getNome() : null,
                m.getObservacao()
        );
    }
}
