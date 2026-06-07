package unitins.gestao.estoqueIA.dto.analise;

import java.math.BigDecimal;

/**
 * Visão executiva do estoque num instante: totais, valor imobilizado e
 * indicadores de risco. Alimenta o dashboard e o resumo em linguagem natural.
 */
public record ResumoEstoque(
        long totalProdutosAtivos,
        long totalCategorias,
        long totalUnidades,
        BigDecimal valorTotalEstoque,
        long produtosAbaixoMinimo,
        long produtosComReposicaoSugerida,
        String categoriaMaisCritica
) {
}
