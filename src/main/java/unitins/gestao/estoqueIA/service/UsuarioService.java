package unitins.gestao.estoqueIA.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unitins.gestao.estoqueIA.dto.usuario.UsuarioResponse;
import unitins.gestao.estoqueIA.dto.usuario.UsuarioUpdateRequest;
import unitins.gestao.estoqueIA.entity.Usuario;
import unitins.gestao.estoqueIA.entity.enums.Role;
import unitins.gestao.estoqueIA.exception.ConflictException;
import unitins.gestao.estoqueIA.exception.NotFoundException;
import unitins.gestao.estoqueIA.repository.UsuarioRepository;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public UsuarioResponse autenticado() {
        return UsuarioResponse.from(buscarAutenticadoOuFalha());
    }

    @Transactional(readOnly = true)
    public Page<UsuarioResponse> listar(Pageable pageable) {
        return repository.findAll(pageable).map(UsuarioResponse::from);
    }

    @Transactional(readOnly = true)
    public UsuarioResponse buscarPorId(Long id) {
        return UsuarioResponse.from(buscarEntidade(id));
    }

    @Transactional
    public UsuarioResponse atualizar(Long id, UsuarioUpdateRequest request) {
        Usuario usuario = buscarEntidade(id);
        garantirProprioOuAdmin(usuario);

        if (!usuario.getEmail().equalsIgnoreCase(request.email())
                && repository.existsByEmail(request.email())) {
            throw new ConflictException("E-mail já em uso: " + request.email());
        }

        usuario.setNome(request.nome());
        usuario.setEmail(request.email());
        if (request.novaSenha() != null && !request.novaSenha().isBlank()) {
            usuario.setSenhaHash(passwordEncoder.encode(request.novaSenha()));
        }
        return UsuarioResponse.from(usuario);
    }

    @Transactional
    public void desativar(Long id) {
        Usuario usuario = buscarEntidade(id);
        usuario.setAtivo(false);
    }

    Usuario buscarAutenticadoOuFalha() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new NotFoundException("Nenhum usuário autenticado");
        }
        return repository.findByEmail(auth.getName())
                .orElseThrow(() -> new NotFoundException("Usuário autenticado não existe mais"));
    }

    private Usuario buscarEntidade(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado: " + id));
    }

    private void garantirProprioOuAdmin(Usuario alvo) {
        Usuario autenticado = buscarAutenticadoOuFalha();
        boolean ehProprio = autenticado.getId().equals(alvo.getId());
        boolean ehAdmin = autenticado.getRole() == Role.ADMIN;
        if (!ehProprio && !ehAdmin) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Você só pode alterar seu próprio usuário"
            );
        }
    }
}
