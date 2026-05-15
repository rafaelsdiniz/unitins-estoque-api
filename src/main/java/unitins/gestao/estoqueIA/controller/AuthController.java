package unitins.gestao.estoqueIA.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import unitins.gestao.estoqueIA.dto.auth.LoginRequest;
import unitins.gestao.estoqueIA.dto.auth.RefreshRequest;
import unitins.gestao.estoqueIA.dto.auth.RegisterRequest;
import unitins.gestao.estoqueIA.dto.auth.TokenResponse;
import unitins.gestao.estoqueIA.entity.RefreshToken;
import unitins.gestao.estoqueIA.entity.Usuario;
import unitins.gestao.estoqueIA.entity.enums.Role;
import unitins.gestao.estoqueIA.exception.NotFoundException;
import unitins.gestao.estoqueIA.repository.UsuarioRepository;
import unitins.gestao.estoqueIA.security.RefreshTokenService;
import unitins.gestao.estoqueIA.security.TokenService;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody @Valid LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.senha())
        );

        Usuario usuario = usuarioRepository.findByEmail(request.email())
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));

        String access = tokenService.gerarToken(auth);
        RefreshToken refresh = refreshTokenService.emitir(usuario);

        return ResponseEntity.ok(
                TokenResponse.of(access, refresh.getToken(), tokenService.getExpirationSeconds())
        );
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@RequestBody @Valid RefreshRequest request) {
        RefreshToken novo = refreshTokenService.rotacionar(request.refreshToken());
        String access = tokenService.gerarToken(novo.getUsuario());

        return ResponseEntity.ok(
                TokenResponse.of(access, novo.getToken(), tokenService.getExpirationSeconds())
        );
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody @Valid RegisterRequest request) {
        if (usuarioRepository.existsByEmail(request.email())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("E-mail já cadastrado");
        }

        Usuario usuario = new Usuario();
        usuario.setNome(request.nome());
        usuario.setEmail(request.email());
        usuario.setSenhaHash(passwordEncoder.encode(request.senha()));
        usuario.setRole(request.role() != null ? request.role() : Role.USUARIO);
        usuario.setAtivo(true);

        usuarioRepository.save(usuario);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
