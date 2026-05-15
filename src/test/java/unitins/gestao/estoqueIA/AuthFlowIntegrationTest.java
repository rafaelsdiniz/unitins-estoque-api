package unitins.gestao.estoqueIA;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void jwtProps(DynamicPropertyRegistry r) {
        r.add("jwt.secret", () -> "chave-de-teste-com-no-minimo-32-caracteres-aleatorios-aqui");
    }

    @LocalServerPort int port;

    HttpClient http = HttpClient.newHttpClient();

    @Test
    void registrarLogarEAcessarEndpointProtegido() throws Exception {
        // 1) registrar
        HttpResponse<String> reg = post("/auth/register",
                """
                {"nome":"Teste","email":"teste@x.com","senha":"senha123","role":"USUARIO"}
                """, null);
        assertThat(reg.statusCode()).isEqualTo(201);

        // 2) login
        HttpResponse<String> login = post("/auth/login",
                """
                {"email":"teste@x.com","senha":"senha123"}
                """, null);
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.body()).contains("accessToken").contains("refreshToken");

        String token = extract(login.body(), "accessToken");

        // 3) sem token → 401
        HttpResponse<String> semToken = get("/categorias", null);
        assertThat(semToken.statusCode()).isEqualTo(401);

        // 4) com token → 200
        HttpResponse<String> comToken = get("/categorias", token);
        assertThat(comToken.statusCode()).isEqualTo(200);

        // 5) USUARIO não pode criar categoria → 403
        HttpResponse<String> forbidden = post("/categorias", """
                {"nome":"X"}
                """, token);
        assertThat(forbidden.statusCode()).isEqualTo(403);
    }

    private HttpResponse<String> post(String path, String body, String bearer) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (bearer != null) b.header("Authorization", "Bearer " + bearer);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET();
        if (bearer != null) b.header("Authorization", "Bearer " + bearer);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String extract(String json, String key) {
        int i = json.indexOf("\"" + key + "\":\"");
        if (i < 0) return null;
        int start = i + key.length() + 4;
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }
}
