package unitins.gestao.estoqueIA.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import unitins.gestao.estoqueIA.dto.movimentacao.MovimentacaoRequest;
import unitins.gestao.estoqueIA.dto.movimentacao.MovimentacaoResponse;
import unitins.gestao.estoqueIA.entity.Movimentacao;
import unitins.gestao.estoqueIA.entity.Produto;
import unitins.gestao.estoqueIA.entity.enums.TipoMovimentacao;
import unitins.gestao.estoqueIA.exception.BusinessException;
import unitins.gestao.estoqueIA.repository.MovimentacaoRepository;
import unitins.gestao.estoqueIA.repository.UsuarioRepository;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovimentacaoServiceTest {

    @Mock MovimentacaoRepository movimentacaoRepository;
    @Mock ProdutoService produtoService;
    @Mock UsuarioRepository usuarioRepository;

    @InjectMocks MovimentacaoService service;

    Produto produto;

    @BeforeEach
    void setUp() {
        produto = new Produto();
        produto.setId(1L);
        produto.setNome("Mouse");
        produto.setQuantidade(10);
        produto.setPrecoUnitario(new BigDecimal("50.00"));
        produto.setAtivo(true);

        lenient().when(produtoService.buscarEntidade(1L)).thenReturn(produto);
        lenient().when(movimentacaoRepository.save(any(Movimentacao.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void entradaAumentaQuantidadeDoProduto() {
        var req = new MovimentacaoRequest(1L, TipoMovimentacao.ENTRADA, 5, "compra");

        MovimentacaoResponse resp = service.registrar(req);

        assertThat(produto.getQuantidade()).isEqualTo(15);
        assertThat(resp.tipo()).isEqualTo(TipoMovimentacao.ENTRADA);
        assertThat(resp.precoUnitarioNaEpoca()).isEqualByComparingTo("50.00");
    }

    @Test
    void saidaDiminuiQuantidadeDoProduto() {
        var req = new MovimentacaoRequest(1L, TipoMovimentacao.SAIDA, 4, null);

        service.registrar(req);

        assertThat(produto.getQuantidade()).isEqualTo(6);
    }

    @Test
    void saidaMaiorQueEstoqueLancaBusinessException() {
        var req = new MovimentacaoRequest(1L, TipoMovimentacao.SAIDA, 100, null);

        assertThatThrownBy(() -> service.registrar(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Estoque insuficiente");

        assertThat(produto.getQuantidade()).isEqualTo(10);
    }

    @Test
    void produtoInativoNaoPodeMovimentar() {
        produto.setAtivo(false);
        var req = new MovimentacaoRequest(1L, TipoMovimentacao.ENTRADA, 1, null);

        assertThatThrownBy(() -> service.registrar(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Produto inativo");
    }
}
