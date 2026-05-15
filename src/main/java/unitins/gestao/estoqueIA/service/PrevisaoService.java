package unitins.gestao.estoqueIA.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unitins.gestao.estoqueIA.dto.previsao.PrevisaoResponse;
import unitins.gestao.estoqueIA.entity.Produto;
import unitins.gestao.estoqueIA.entity.enums.TipoMovimentacao;
import unitins.gestao.estoqueIA.repository.MovimentacaoRepository;
import unitins.gestao.estoqueIA.repository.ProdutoRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Previsão simples por média móvel sobre o histórico de SAÍDAS.
 *
 * Algoritmo:
 *   1. Soma saídas dos últimos N dias (janela configurável, default 30)
 *   2. consumoMedioDiario = saidasNaJanela / N
 *   3. diasAteRuptura     = quantidade / consumoMedioDiario
 *   4. Sugere reposição quando diasAteRuptura <= tempoReposicaoDias
 *      OU quando quantidade já está abaixo do estoqueMinimo
 *
 * Aprimorações futuras: suavização exponencial, sazonalidade, ML.
 */
@Service
@RequiredArgsConstructor
public class PrevisaoService {

    private static final int JANELA_PADRAO_DIAS = 30;

    private final ProdutoRepository produtoRepository;
    private final MovimentacaoRepository movimentacaoRepository;
    private final ProdutoService produtoService;

    @Transactional(readOnly = true)
    public PrevisaoResponse prever(Long produtoId, Integer janelaDias) {
        Produto produto = produtoService.buscarEntidade(produtoId);
        return calcular(produto, janelaDias != null ? janelaDias : JANELA_PADRAO_DIAS);
    }

    @Transactional(readOnly = true)
    public List<PrevisaoResponse> reposicaoSugerida(Integer janelaDias) {
        int janela = janelaDias != null ? janelaDias : JANELA_PADRAO_DIAS;
        return produtoRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getAtivo()))
                .map(p -> calcular(p, janela))
                .filter(PrevisaoResponse::reposicaoSugerida)
                .toList();
    }

    private PrevisaoResponse calcular(Produto produto, int janelaDias) {
        LocalDateTime desde = LocalDateTime.now().minusDays(janelaDias);
        long saidas = movimentacaoRepository.somarQuantidadeDesde(
                produto.getId(), TipoMovimentacao.SAIDA, desde
        );

        BigDecimal consumoMedio = BigDecimal.valueOf(saidas)
                .divide(BigDecimal.valueOf(janelaDias), 4, RoundingMode.HALF_UP);

        Integer diasAteRuptura = calcularDias(produto.getQuantidade(), consumoMedio);
        Integer diasAteMinimo = calcularDias(
                produto.getQuantidade() - produto.getEstoqueMinimo(),
                consumoMedio
        );

        boolean abaixoMinimo = produto.getQuantidade() < produto.getEstoqueMinimo();
        int tempoReposicao = produto.getTempoReposicaoDias() != null
                ? produto.getTempoReposicaoDias()
                : 0;
        boolean ruptureProxima = diasAteRuptura != null && diasAteRuptura <= tempoReposicao;

        boolean sugerir = abaixoMinimo || ruptureProxima;
        String motivo;
        if (abaixoMinimo) {
            motivo = "Quantidade abaixo do estoque mínimo";
        } else if (ruptureProxima) {
            motivo = "Ruptura prevista em " + diasAteRuptura + " dia(s); tempo de reposição = " + tempoReposicao;
        } else if (consumoMedio.signum() == 0) {
            motivo = "Sem saídas na janela — consumo zero";
        } else {
            motivo = "Estoque adequado";
        }

        return new PrevisaoResponse(
                produto.getId(),
                produto.getNome(),
                produto.getQuantidade(),
                produto.getEstoqueMinimo(),
                produto.getTempoReposicaoDias(),
                janelaDias,
                saidas,
                consumoMedio,
                diasAteRuptura,
                diasAteMinimo,
                sugerir,
                motivo
        );
    }

    private Integer calcularDias(int saldo, BigDecimal consumoMedio) {
        if (consumoMedio.signum() <= 0) return null;
        if (saldo <= 0) return 0;
        return BigDecimal.valueOf(saldo)
                .divide(consumoMedio, 0, RoundingMode.FLOOR)
                .intValueExact();
    }
}
