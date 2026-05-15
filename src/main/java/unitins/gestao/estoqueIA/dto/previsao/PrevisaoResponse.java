package unitins.gestao.estoqueIA.dto.previsao;

import java.math.BigDecimal;

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
        String motivo
) {
}
