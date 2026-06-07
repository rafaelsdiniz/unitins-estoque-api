package unitins.gestao.estoqueIA.dto.analise;

import java.math.BigDecimal;

/**
 * Anomalia detectada no comportamento de um produto.
 *   tipo = PICO_SAIDA       → uma saída muito acima do consumo médio
 *   tipo = ESTOQUE_PARADO   → há estoque mas nenhuma saída na janela
 */
public record AnomaliaResponse(
        Long produtoId,
        String produtoNome,
        String tipo,
        String descricao,
        BigDecimal valorReferencia,
        BigDecimal valorObservado
) {
}
