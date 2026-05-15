package unitins.gestao.estoqueIA.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unitins.gestao.estoqueIA.dto.categoria.CategoriaRequest;
import unitins.gestao.estoqueIA.dto.categoria.CategoriaResponse;
import unitins.gestao.estoqueIA.entity.Categoria;
import unitins.gestao.estoqueIA.exception.ConflictException;
import unitins.gestao.estoqueIA.exception.NotFoundException;
import unitins.gestao.estoqueIA.repository.CategoriaRepository;

@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository repository;

    @Transactional(readOnly = true)
    public Page<CategoriaResponse> listar(Pageable pageable) {
        return repository.findAll(pageable).map(CategoriaResponse::from);
    }

    @Transactional(readOnly = true)
    public CategoriaResponse buscarPorId(Long id) {
        return CategoriaResponse.from(buscarEntidade(id));
    }

    @Transactional
    public CategoriaResponse criar(CategoriaRequest request) {
        if (repository.existsByNomeIgnoreCase(request.nome())) {
            throw new ConflictException("Categoria já cadastrada: " + request.nome());
        }
        Categoria categoria = new Categoria();
        categoria.setNome(request.nome());
        categoria.setDescricao(request.descricao());
        return CategoriaResponse.from(repository.save(categoria));
    }

    @Transactional
    public CategoriaResponse atualizar(Long id, CategoriaRequest request) {
        Categoria categoria = buscarEntidade(id);

        if (!categoria.getNome().equalsIgnoreCase(request.nome())
                && repository.existsByNomeIgnoreCase(request.nome())) {
            throw new ConflictException("Categoria já cadastrada: " + request.nome());
        }

        categoria.setNome(request.nome());
        categoria.setDescricao(request.descricao());
        return CategoriaResponse.from(categoria);
    }

    @Transactional
    public void deletar(Long id) {
        Categoria categoria = buscarEntidade(id);
        repository.delete(categoria);
    }

    Categoria buscarEntidade(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Categoria não encontrada: " + id));
    }
}
