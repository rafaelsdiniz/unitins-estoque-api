package unitins.gestao.estoqueIA.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unitins.gestao.estoqueIA.dto.movimentacao.MovimentacaoRequest;
import unitins.gestao.estoqueIA.dto.movimentacao.MovimentacaoResponse;
import unitins.gestao.estoqueIA.entity.Movimentacao;
import unitins.gestao.estoqueIA.entity.Produto;
import unitins.gestao.estoqueIA.entity.Usuario;
import unitins.gestao.estoqueIA.entity.enums.TipoMovimentacao;
import unitins.gestao.estoqueIA.exception.BusinessException;
import unitins.gestao.estoqueIA.repository.MovimentacaoRepository;
import unitins.gestao.estoqueIA.repository.UsuarioRepository;

@Service
@RequiredArgsConstructor
public class MovimentacaoService {

    private final MovimentacaoRepository repository;
    private final ProdutoService produtoService;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public Page<MovimentacaoResponse> listarPorProduto(Long produtoId, Pageable pageable) {
        return repository.findByProdutoIdOrderByDataHoraDesc(produtoId, pageable)
                .map(MovimentacaoResponse::from);
    }

    @Transactional
    public MovimentacaoResponse registrar(MovimentacaoRequest request) {
        Produto produto = produtoService.buscarEntidade(request.produtoId());

        if (Boolean.FALSE.equals(produto.getAtivo())) {
            throw new BusinessException("Produto inativo: não é possível movimentar");
        }

        int novaQuantidade = calcularNovaQuantidade(produto, request.tipo(), request.quantidade());
        produto.setQuantidade(novaQuantidade);

        Movimentacao movimentacao = new Movimentacao();
        movimentacao.setProduto(produto);
        movimentacao.setTipo(request.tipo());
        movimentacao.setQuantidade(request.quantidade());
        movimentacao.setPrecoUnitarioNaEpoca(produto.getPrecoUnitario());
        movimentacao.setObservacao(request.observacao());
        movimentacao.setUsuario(usuarioAutenticado());

        return MovimentacaoResponse.from(repository.save(movimentacao));
    }

    private int calcularNovaQuantidade(Produto produto, TipoMovimentacao tipo, int quantidade) {
        if (tipo == TipoMovimentacao.ENTRADA) {
            return produto.getQuantidade() + quantidade;
        }
        int resultado = produto.getQuantidade() - quantidade;
        if (resultado < 0) {
            throw new BusinessException(
                    "Estoque insuficiente. Disponível: " + produto.getQuantidade()
                            + ", solicitado: " + quantidade
            );
        }
        return resultado;
    }

    private Usuario usuarioAutenticado() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        return usuarioRepository.findByEmail(auth.getName()).orElse(null);
    }
}
