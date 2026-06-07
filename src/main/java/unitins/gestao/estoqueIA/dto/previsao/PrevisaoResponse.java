package unitins.gestao.estoqueIA.dto.previsao;

import java.math.BigDecimal;

/**
 * Resultado da previsão de reposição de um produto.
 *
 * Campos clássicos (v1): consumo médio simples, dias até ruptura, sugestão.
 * Campos v2 (inteligência adicional):
 *   - consumoMedioPonderado: média móvel exponencial (EWMA), reage mais a mudanças recentes
 *   - tendencia: ALTA | BAIXA | ESTAVEL | SEM_DADOS (consumo subindo/caindo na janela)
 *   - quantidadeSugerida: quanto comprar para cobrir o lead time + manter o estoque mínimo
 *   - nivelConfianca: ALTA | MEDIA | BAIXA, conforme o nº de movimentações na janela
 */
public record PrevisaoResponse(
        Long produtoId,
        String produtoNome,
        Integer quantidadeAtual,
        Integer estoqueMinimo,
        Integer tempoReposicaoDias,
        int janelaDias,
        long saidasNaJanela,
        BigDecimal consumoMedioDiario,
        Integer diasAteRuptura,
        Integer diasAteEstoqueMinimo,
        boolean reposicaoSugerida,
        String motivo,
        BigDecimal consumoMedioPonderado,
        String tendencia,
        Integer quantidadeSugerida,
        String nivelConfianca,
        long movimentacoesSaidaNaJanela
) {
}
