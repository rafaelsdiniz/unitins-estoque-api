package unitins.gestao.estoqueIA.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import unitins.gestao.estoqueIA.entity.Produto;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    Optional<Produto> findByCodigo(String codigo);

    Page<Produto> findByNomeContainingIgnoreCaseAndAtivoTrue(String nome, Pageable pageable);

    @Query("SELECT p FROM Produto p WHERE p.ativo = true AND p.quantidade < p.estoqueMinimo")
    List<Produto> findProdutosComBaixoEstoque();
}
