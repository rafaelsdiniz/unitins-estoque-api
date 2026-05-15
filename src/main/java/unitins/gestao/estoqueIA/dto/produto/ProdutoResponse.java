package unitins.gestao.estoqueIA.dto.produto;

import unitins.gestao.estoqueIA.entity.Produto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProdutoResponse(
        Long id,
        String codigo,
        String nome,
        String descricao,
        BigDecimal precoUnitario,
        Integer quantidade,
        Integer estoqueMinimo,
        Integer tempoReposicaoDias,
        Long categoriaId,
        String categoriaNome,
        Boolean ativo,
        LocalDateTime dataCriacao,
        LocalDateTime dataAtualizacao
) {
    public static ProdutoResponse from(Produto p) {
        return new ProdutoResponse(
                p.getId(),
                p.getCodigo(),
                p.getNome(),
                p.getDescricao(),
                p.getPrecoUnitario(),
                p.getQuantidade(),
                p.getEstoqueMinimo(),
                p.getTempoReposicaoDias(),
                p.getCategoria() != null ? p.getCategoria().getId() : null,
                p.getCategoria() != null ? p.getCategoria().getNome() : null,
                p.getAtivo(),
                p.getDataCriacao(),
                p.getDataAtualizacao()
        );
    }
}
