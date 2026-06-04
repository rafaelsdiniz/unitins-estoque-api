package unitins.gestao.estoqueIA.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import unitins.gestao.estoqueIA.dto.ia.AssistenteRequest;
import unitins.gestao.estoqueIA.dto.ia.AssistenteResponse;
import unitins.gestao.estoqueIA.dto.ia.ChatRequest;
import unitins.gestao.estoqueIA.dto.ia.ChatResponse;
import unitins.gestao.estoqueIA.service.ia.IaAssistenteService;

@RestController
@RequestMapping("/ia")
@RequiredArgsConstructor
public class IaController {

    private final IaAssistenteService service;

    /** Assistente de turno único, focado em reposição. */
    @PostMapping("/assistente")
    public AssistenteResponse assistente(@RequestBody @Valid AssistenteRequest request) {
        return new AssistenteResponse(service.responder(request.pergunta()));
    }

    /** Chat de dúvidas multi-turno (histórico enviado pelo cliente). */
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody @Valid ChatRequest request) {
        return new ChatResponse(service.responderChat(request.mensagens()));
    }
}
