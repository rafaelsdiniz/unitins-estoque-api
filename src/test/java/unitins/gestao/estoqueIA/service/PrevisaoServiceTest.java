package unitins.gestao.estoqueIA.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import unitins.gestao.estoqueIA.dto.previsao.PrevisaoResponse;
import unitins.gestao.estoqueIA.entity.Produto;
import unitins.gestao.estoqueIA.entity.enums.TipoMovimentacao;
import unitins.gestao.estoqueIA.repository.MovimentacaoRepository;
import unitins.gestao.estoqueIA.repository.ProdutoRepository;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrevisaoServiceTest {

    @Mock ProdutoRepository produtoRepository;
    @Mock MovimentacaoRepository movimentacaoRepository;
    @Mock ProdutoService produtoService;

    @InjectMocks PrevisaoService service;

    Produto produto;

    @BeforeEach
    void setUp() {
        produto = new Produto();
        produto.setId(1L);
        produto.setNome("Mouse");
        produto.setQuantidade(60);
        produto.setEstoqueMinimo(10);
        produto.setTempoReposicaoDias(7);
        produto.setAtivo(true);
    }

    @Test
    void preverConsumoMedioComJanelaDe30Dias() {
        // 60 unidades saíram em 30 dias = 2/dia → 60 atual / 2 = 30 dias até ruptura
        when(produtoService.buscarEntidade(1L)).thenReturn(produto);
        when(movimentacaoRepository.somarQuantidadeDesde(eq(1L), eq(TipoMovimentacao.SAIDA), any(LocalDateTime.class)))
                .thenReturn(60L);

        PrevisaoResponse r = service.prever(1L, 30);

        assertThat(r.saidasNaJanela()).isEqualTo(60);
        assertThat(r.consumoMedioDiario()).isEqualByComparingTo("2.0000");
        assertThat(r.diasAteRuptura()).isEqualTo(30);
        assertThat(r.reposicaoSugerida()).isFalse();
    }

    @Test
    void sugereReposicaoQuandoRupturaProximaDoTempoDeReposicao() {
        // 5/dia, estoque 30 → ruptura em 6 dias, mas tempoReposicao=7 → SUGERIR
        produto.setQuantidade(30);
        when(produtoService.buscarEntidade(1L)).thenReturn(produto);
        when(movimentacaoRepository.somarQuantidadeDesde(eq(1L), eq(TipoMovimentacao.SAIDA), any()))
                .thenReturn(150L);

        PrevisaoResponse r = service.prever(1L, 30);

        assertThat(r.diasAteRuptura()).isEqualTo(6);
        assertThat(r.reposicaoSugerida()).isTrue();
        assertThat(r.motivo()).contains("Ruptura prevista");
    }

    @Test
    void sugereReposicaoQuandoEstoqueAbaixoDoMinimo() {
        produto.setQuantidade(5);
        when(produtoService.buscarEntidade(1L)).thenReturn(produto);
        when(movimentacaoRepository.somarQuantidadeDesde(eq(1L), eq(TipoMovimentacao.SAIDA), any()))
                .thenReturn(30L);

        PrevisaoResponse r = service.prever(1L, 30);

        assertThat(r.reposicaoSugerida()).isTrue();
        assertThat(r.motivo()).contains("abaixo do estoque mínimo");
    }

    @Test
    void semSaidasRetornaDiasAteRupturaNulo() {
        when(produtoService.buscarEntidade(1L)).thenReturn(produto);
        when(movimentacaoRepository.somarQuantidadeDesde(eq(1L), eq(TipoMovimentacao.SAIDA), any()))
                .thenReturn(0L);

        PrevisaoResponse r = service.prever(1L, 30);

        assertThat(r.consumoMedioDiario()).isEqualByComparingTo("0.0000");
        assertThat(r.diasAteRuptura()).isNull();
        assertThat(r.reposicaoSugerida()).isFalse();
        assertThat(r.motivo()).contains("consumo zero");
    }
}
