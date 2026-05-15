package unitins.gestao.estoqueIA.dto.categoria;

import unitins.gestao.estoqueIA.entity.Categoria;

public record CategoriaResponse(
        Long id,
        String nome,
        String descricao
) {
    public static CategoriaResponse from(Categoria c) {
        return new CategoriaResponse(c.getId(), c.getNome(), c.getDescricao());
    }
}
