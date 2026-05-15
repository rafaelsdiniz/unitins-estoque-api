package unitins.gestao.estoqueIA.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import unitins.gestao.estoqueIA.entity.enums.Role;

public record RegisterRequest(

        @NotBlank
        @Size(max = 120)
        String nome,

        @NotBlank
        @Email
        @Size(max = 150)
        String email,

        @NotBlank
        @Size(min = 6, max = 100)
        String senha,

        Role role
) {
}
