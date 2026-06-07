package unitins.gestao.estoqueIA.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unitins.gestao.estoqueIA.dto.analise.AnomaliaResponse;
import unitins.gestao.estoqueIA.dto.analise.CurvaAbcItem;
import unitins.gestao.estoqueIA.dto.analise.ResumoEstoque;
import unitins.gestao.estoqueIA.entity.Movimentacao;
import unitins.gestao.estoqueIA.entity.Produto;
import unitins.gestao.estoqueIA.entity.enums.TipoMovimentacao;
import unitins.gestao.estoqueIA.repository.CategoriaRepository;
import unitins.gestao.estoqueIA.repository.MovimentacaoRepository;
import unitins.gestao.estoqueIA.repository.ProdutoRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Análises agregadas do estoque (tudo determinístico). Serve tanto à API REST
 * (dashboard) quanto à IA, que usa estes números como contexto/ferramenta.
 */
@Service
@RequiredArgsConstructor
public class EstoqueAnaliseService {

    private static final int JANELA_PADRAO_DIAS = 30;
    /** Acima de quantas vezes o consumo médio diário uma saída é considerada pico. */
    private static final BigDecimal FATOR_PICO = BigDecimal.valueOf(5);

    private final ProdutoRepository produtoRepository;
    private final MovimentacaoRepository movimentacaoRepository;
    private final CategoriaRepository categoriaRepository;
    private final PrevisaoService previsaoService;

    @Transactional(readOnly = true)
    public ResumoEstoque resumo() {
        List<Produto> ativos = produtosAtivos();

        long totalUnidades = ativos.stream()
                .mapToLong(Produto::getQuantidade)
                .sum();

        BigDecimal valorTotal = ativos.stream()
                .map(p -> p.getPrecoUnitario().multiply(BigDecimal.valueOf(p.getQuantidade())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        long abaixoMinimo = ativos.stream()
                .filter(p -> p.getQuantidade() < p.getEstoqueMinimo())
                .count();

        long reposicao = previsaoService.reposicaoSugerida(null).size();

        String categoriaCritica = categoriaMaisCritica(ativos);

        return new ResumoEstoque(
                ativos.size(),
                categoriaRepository.count(),
                totalUnidades,
                valorTotal,
                abaixoMinimo,
                reposicao,
                categoriaCritica
        );
    }

    @Transactional(readOnly = true)
    public List<CurvaAbcItem> curvaAbc() {
        List<Produto> ativos = produtosAtivos().stream()
                .sorted(Comparator.comparing(this::valorEstoque).reversed())
                .toList();

        BigDecimal total = ativos.stream()
                .map(this::valorEstoque)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CurvaAbcItem> itens = new ArrayList<>();
        if (total.signum() == 0) {
            return itens;
        }

        BigDecimal acumulado = BigDecimal.ZERO;
        for (Produto p : ativos) {
            BigDecimal valor = valorEstoque(p);
            acumulado = acumulado.add(valor);

            BigDecimal pct = percentual(valor, total);
            BigDecimal pctAcum = percentual(acumulado, total);
            String classe = pctAcum.compareTo(BigDecimal.valueOf(80)) <= 0 ? "A"
                    : pctAcum.compareTo(BigDecimal.valueOf(95)) <= 0 ? "B"
                    : "C";

            itens.add(new CurvaAbcItem(p.getId(), p.getNome(), valor, pct, pctAcum, classe));
        }
        return itens;
    }

    @Transactional(readOnly = true)
    public List<AnomaliaResponse> anomalias(Integer janelaDias) {
        int janela = janelaDias != null ? janelaDias : JANELA_PADRAO_DIAS;
        LocalDateTime desde = LocalDateTime.now().minusDays(janela);
        List<AnomaliaResponse> anomalias = new ArrayList<>();

        for (Produto p : produtosAtivos()) {
            List<Movimentacao> saidas = movimentacaoRepository
                    .findByProdutoIdAndTipoAndDataHoraGreaterThanEqualOrderByDataHoraAsc(
                            p.getId(), TipoMovimentacao.SAIDA, desde);

            if (saidas.isEmpty()) {
                if (p.getQuantidade() > 0) {
                    anomalias.add(new AnomaliaResponse(
                            p.getId(), p.getNome(), "ESTOQUE_PARADO",
                            "Sem saídas nos últimos " + janela + " dia(s), mas há "
                                    + p.getQuantidade() + " unidade(s) em estoque.",
                            BigDecimal.ZERO, BigDecimal.valueOf(p.getQuantidade())));
                }
                continue;
            }

            long total = saidas.stream().mapToLong(Movimentacao::getQuantidade).sum();
            BigDecimal mediaDiaria = BigDecimal.valueOf(total)
                    .divide(BigDecimal.valueOf(janela), 4, RoundingMode.HALF_UP);
            int maiorSaida = saidas.stream().mapToInt(Movimentacao::getQuantidade).max().orElse(0);

            BigDecimal limite = mediaDiaria.multiply(FATOR_PICO);
            if (mediaDiaria.signum() > 0 && BigDecimal.valueOf(maiorSaida).compareTo(limite) > 0) {
                anomalias.add(new AnomaliaResponse(
                        p.getId(), p.getNome(), "PICO_SAIDA",
                        "Saída de " + maiorSaida + " unidade(s) muito acima do consumo médio diário ("
                                + mediaDiaria.stripTrailingZeros().toPlainString() + "/dia).",
                        limite.setScale(2, RoundingMode.HALF_UP),
                        BigDecimal.valueOf(maiorSaida)));
            }
        }
        return anomalias;
    }

    // ===== helpers =====

    private List<Produto> produtosAtivos() {
        return produtoRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getAtivo()))
                .toList();
    }

    private BigDecimal valorEstoque(Produto p) {
        return p.getPrecoUnitario().multiply(BigDecimal.valueOf(p.getQuantidade()));
    }

    private BigDecimal percentual(BigDecimal parte, BigDecimal total) {
        return parte.multiply(BigDecimal.valueOf(100))
                .divide(total, 2, RoundingMode.HALF_UP);
    }

    /** Categoria com mais produtos abaixo do mínimo (proxy de criticidade). */
    private String categoriaMaisCritica(List<Produto> ativos) {
        Map<String, Long> porCategoria = ativos.stream()
                .filter(p -> p.getQuantidade() < p.getEstoqueMinimo())
                .filter(p -> p.getCategoria() != null)
                .collect(Collectors.groupingBy(p -> p.getCategoria().getNome(), Collectors.counting()));

        return porCategoria.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}
