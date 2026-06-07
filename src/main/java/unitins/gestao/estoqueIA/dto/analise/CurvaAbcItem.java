package unitins.gestao.estoqueIA.dto.analise;

import java.math.BigDecimal;

/**
 * Item da curva ABC. Classifica os produtos pelo valor imobilizado em estoque:
 *   A = poucos itens que concentram a maior parte do valor (até 80% acumulado)
 *   B = intermediários (até 95% acumulado)
 *   C = a longa cauda de baixo valor (restante)
 */
public record CurvaAbcItem(
        Long produtoId,
        String produtoNome,
        BigDecimal valorEstoque,
        BigDecimal percentualDoTotal,
        BigDecimal percentualAcumulado,
        String classe
) {
}
