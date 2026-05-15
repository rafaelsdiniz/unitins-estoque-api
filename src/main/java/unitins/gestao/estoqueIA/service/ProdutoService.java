package unitins.gestao.estoqueIA.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unitins.gestao.estoqueIA.dto.produto.ProdutoRequest;
import unitins.gestao.estoqueIA.dto.produto.ProdutoResponse;
import unitins.gestao.estoqueIA.entity.Categoria;
import unitins.gestao.estoqueIA.entity.Produto;
import unitins.gestao.estoqueIA.exception.NotFoundException;
import unitins.gestao.estoqueIA.repository.ProdutoRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProdutoService {

    private final ProdutoRepository repository;
    private final CategoriaService categoriaService;

    @Transactional(readOnly = true)
    public Page<ProdutoResponse> listar(String nome, Pageable pageable) {
        Page<Produto> page = (nome == null || nome.isBlank())
                ? repository.findAll(pageable)
                : repository.findByNomeContainingIgnoreCaseAndAtivoTrue(nome, pageable);
        return page.map(ProdutoResponse::from);
    }

    @Transactional(readOnly = true)
    public ProdutoResponse buscarPorId(Long id) {
        return ProdutoResponse.from(buscarEntidade(id));
    }

    @Transactional(readOnly = true)
    public List<ProdutoResponse> listarBaixoEstoque() {
        return repository.findProdutosComBaixoEstoque().stream()
                .map(ProdutoResponse::from)
                .toList();
    }

    @Transactional
    public ProdutoResponse criar(ProdutoRequest request) {
        Produto produto = new Produto();
        aplicar(produto, request);
        produto.setQuantidade(0);
        produto.setAtivo(true);
        return ProdutoResponse.from(repository.save(produto));
    }

    @Transactional
    public ProdutoResponse atualizar(Long id, ProdutoRequest request) {
        Produto produto = buscarEntidade(id);
        aplicar(produto, request);
        return ProdutoResponse.from(produto);
    }

    @Transactional
    public void desativar(Long id) {
        Produto produto = buscarEntidade(id);
        produto.setAtivo(false);
    }

    Produto buscarEntidade(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Produto não encontrado: " + id));
    }

    private void aplicar(Produto produto, ProdutoRequest request) {
        produto.setNome(request.nome());
        produto.setDescricao(request.descricao());
        produto.setPrecoUnitario(request.precoUnitario());
        produto.setEstoqueMinimo(request.estoqueMinimo() != null ? request.estoqueMinimo() : 0);
        produto.setTempoReposicaoDias(request.tempoReposicaoDias());

        if (request.categoriaId() != null) {
            Categoria categoria = categoriaService.buscarEntidade(request.categoriaId());
            produto.setCategoria(categoria);
        } else {
            produto.setCategoria(null);
        }
    }
}
