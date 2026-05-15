package unitins.gestao.estoqueIA.dto.usuario;

import unitins.gestao.estoqueIA.entity.Usuario;
import unitins.gestao.estoqueIA.entity.enums.Role;

import java.time.LocalDateTime;

public record UsuarioResponse(
        Long id,
        String nome,
        String email,
        Role role,
        Boolean ativo,
        LocalDateTime dataCriacao
) {
    public static UsuarioResponse from(Usuario u) {
        return new UsuarioResponse(
                u.getId(),
                u.getNome(),
                u.getEmail(),
                u.getRole(),
                u.getAtivo(),
                u.getDataCriacao()
        );
    }
}
