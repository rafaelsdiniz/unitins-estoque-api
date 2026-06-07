package unitins.gestao.estoqueIA.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unitins.gestao.estoqueIA.dto.previsao.PrevisaoResponse;
import unitins.gestao.estoqueIA.entity.Movimentacao;
import unitins.gestao.estoqueIA.entity.Produto;
import unitins.gestao.estoqueIA.entity.enums.TipoMovimentacao;
import unitins.gestao.estoqueIA.repository.MovimentacaoRepository;
import unitins.gestao.estoqueIA.repository.ProdutoRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Previsão de consumo e reposição sobre o histórico de SAÍDAS.
 *
 * Algoritmo base (v1 — determinístico e testável):
 *   1. Soma saídas dos últimos N dias (janela configurável, default 30)
 *   2. consumoMedioDiario = saidasNaJanela / N
 *   3. diasAteRuptura     = quantidade / consumoMedioDiario
 *   4. Sugere reposição quando diasAteRuptura <= tempoReposicaoDias
 *      OU quando quantidade já está abaixo do estoqueMinimo
 *
 * Inteligência adicional (v2):
 *   - EWMA (média móvel exponencial) sobre o consumo diário → reage mais rápido a
 *     mudanças recentes de demanda do que a média simples.
 *   - Tendência (ALTA/BAIXA/ESTAVEL) comparando a 1ª e a 2ª metade da janela.
 *   - Quantidade sugerida de compra = cobrir o lead time + repor o estoque mínimo.
 *   - Nível de confiança conforme o nº de movimentações observadas na janela.
 */
@Service
@RequiredArgsConstructor
public class PrevisaoService {

    private static final int JANELA_PADRAO_DIAS = 30;
    /** Peso do dado mais recente na EWMA (0..1). Quanto maior, mais reativa. */
    private static final double ALPHA_EWMA = 0.3;

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

        // Consumo médio simples (mantido como base confiável e testada).
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

        // ---- Inteligência adicional (v2) ----
        List<Movimentacao> saidasJanela = movimentacaoRepository
                .findByProdutoIdAndTipoAndDataHoraGreaterThanEqualOrderByDataHoraAsc(
                        produto.getId(), TipoMovimentacao.SAIDA, desde);
        long[] consumoPorDia = consumoDiario(saidasJanela, janelaDias, desde);
        long movimentacoesSaida = saidasJanela.size();

        BigDecimal consumoPonderado = calcularEwma(consumoPorDia);
        String tendencia = calcularTendencia(consumoPorDia);
        Integer quantidadeSugerida = calcularQuantidadeSugerida(
                produto, tempoReposicao, consumoPonderado, consumoMedio);
        String nivelConfianca = calcularConfianca(movimentacoesSaida);

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
                motivo,
                consumoPonderado,
                tendencia,
                quantidadeSugerida,
                nivelConfianca,
                movimentacoesSaida
        );
    }

    private Integer calcularDias(int saldo, BigDecimal consumoMedio) {
        if (consumoMedio.signum() <= 0) return null;
        if (saldo <= 0) return 0;
        return BigDecimal.valueOf(saldo)
                .divide(consumoMedio, 0, RoundingMode.FLOOR)
                .intValueExact();
    }

    /**
     * Agrupa as SAÍDAS da janela em baldes diários (índice 0 = dia mais antigo).
     * Dias sem saída ficam com 0, o que é importante para a EWMA e a tendência.
     */
    private long[] consumoDiario(List<Movimentacao> saidas, int janelaDias, LocalDateTime desde) {
        long[] porDia = new long[janelaDias];
        LocalDate base = desde.toLocalDate();
        for (Movimentacao m : saidas) {
            int idx = (int) ChronoUnit.DAYS.between(base, m.getDataHora().toLocalDate());
            if (idx < 0) idx = 0;
            if (idx >= janelaDias) idx = janelaDias - 1;
            porDia[idx] += m.getQuantidade();
        }
        return porDia;
    }

    /** Média móvel exponencial do consumo diário (unidades/dia). */
    private BigDecimal calcularEwma(long[] porDia) {
        if (porDia.length == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        double ewma = porDia[0];
        for (int i = 1; i < porDia.length; i++) {
            ewma = ALPHA_EWMA * porDia[i] + (1 - ALPHA_EWMA) * ewma;
        }
        return BigDecimal.valueOf(ewma).setScale(4, RoundingMode.HALF_UP);
    }

    /** Compara a 1ª metade da janela (mais antiga) com a 2ª (mais recente). */
    private String calcularTendencia(long[] porDia) {
        int meio = porDia.length / 2;
        long antigo = 0;
        long recente = 0;
        for (int i = 0; i < meio; i++) antigo += porDia[i];
        for (int i = meio; i < porDia.length; i++) recente += porDia[i];

        if (antigo == 0 && recente == 0) return "SEM_DADOS";
        if (recente > antigo * 1.2) return "ALTA";
        if (recente < antigo * 0.8) return "BAIXA";
        return "ESTAVEL";
    }

    /**
     * Quanto comprar para cobrir a demanda durante o tempo de reposição e ainda
     * manter o estoque mínimo como segurança. Usa a EWMA quando há consumo
     * recente; caso contrário, cai para a média simples. Nunca negativo.
     */
    private Integer calcularQuantidadeSugerida(Produto produto, int tempoReposicao,
                                               BigDecimal ewma, BigDecimal consumoMedio) {
        BigDecimal consumoPlanejamento = ewma.signum() > 0 ? ewma : consumoMedio;
        BigDecimal alvo = consumoPlanejamento
                .multiply(BigDecimal.valueOf(tempoReposicao))
                .add(BigDecimal.valueOf(produto.getEstoqueMinimo()));
        int sugerida = alvo.setScale(0, RoundingMode.CEILING).intValue() - produto.getQuantidade();
        return Math.max(sugerida, 0);
    }

    private String calcularConfianca(long movimentacoesSaida) {
        if (movimentacoesSaida >= 10) return "ALTA";
        if (movimentacoesSaida >= 3) return "MEDIA";
        return "BAIXA";
    }
}
