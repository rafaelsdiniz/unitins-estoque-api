package unitins.gestao.estoqueIA.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import unitins.gestao.estoqueIA.entity.Movimentacao;
import unitins.gestao.estoqueIA.entity.enums.TipoMovimentacao;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MovimentacaoRepository extends JpaRepository<Movimentacao, Long> {

    Page<Movimentacao> findByProdutoIdOrderByDataHoraDesc(Long produtoId, Pageable pageable);

    List<Movimentacao> findByProdutoIdAndDataHoraBetween(
            Long produtoId,
            LocalDateTime inicio,
            LocalDateTime fim
    );

    /**
     * Movimentações de um tipo (ex.: SAIDA) a partir de uma data, em ordem
     * cronológica. Base para a média ponderada (EWMA) e a tendência.
     */
    List<Movimentacao> findByProdutoIdAndTipoAndDataHoraGreaterThanEqualOrderByDataHoraAsc(
            Long produtoId,
            TipoMovimentacao tipo,
            LocalDateTime desde
    );

    @Query("""
        SELECT COALESCE(SUM(m.quantidade), 0)
        FROM Movimentacao m
        WHERE m.produto.id = :produtoId
          AND m.tipo = :tipo
          AND m.dataHora >= :desde
        """)
    long somarQuantidadeDesde(
            @Param("produtoId") Long produtoId,
            @Param("tipo") TipoMovimentacao tipo,
            @Param("desde") LocalDateTime desde
    );
}
