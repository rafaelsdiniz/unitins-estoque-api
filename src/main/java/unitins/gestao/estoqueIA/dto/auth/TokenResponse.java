package unitins.gestao.estoqueIA.dto.auth;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
    public static TokenResponse of(String access, String refresh, long expiresIn) {
        return new TokenResponse(access, refresh, "Bearer", expiresIn);
    }
}
