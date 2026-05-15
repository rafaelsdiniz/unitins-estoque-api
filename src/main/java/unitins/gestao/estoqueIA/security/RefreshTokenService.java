package unitins.gestao.estoqueIA.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unitins.gestao.estoqueIA.entity.RefreshToken;
import unitins.gestao.estoqueIA.entity.Usuario;
import unitins.gestao.estoqueIA.exception.BusinessException;
import unitins.gestao.estoqueIA.repository.RefreshTokenRepository;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 64;

    private final RefreshTokenRepository repository;

    @Value("${jwt.refresh-expiration-seconds}")
    private long expirationSeconds;

    @Transactional
    public RefreshToken emitir(Usuario usuario) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken rt = new RefreshToken();
        rt.setToken(token);
        rt.setUsuario(usuario);
        rt.setExpiraEm(LocalDateTime.now().plusSeconds(expirationSeconds));
        rt.setRevogado(false);
        return repository.save(rt);
    }

    @Transactional
    public RefreshToken rotacionar(String tokenAntigo) {
        RefreshToken atual = repository.findByToken(tokenAntigo)
                .orElseThrow(() -> new BusinessException("Refresh token inválido"));

        if (!atual.isValido()) {
            throw new BusinessException("Refresh token expirado ou revogado");
        }

        atual.setRevogado(true);
        return emitir(atual.getUsuario());
    }

    @Transactional
    public void revogarTodos(Long usuarioId) {
        repository.revogarTodosDoUsuario(usuarioId);
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
