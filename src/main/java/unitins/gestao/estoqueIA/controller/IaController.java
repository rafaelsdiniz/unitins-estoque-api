package unitins.gestao.estoqueIA.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import unitins.gestao.estoqueIA.dto.ia.AssistenteRequest;
import unitins.gestao.estoqueIA.dto.ia.AssistenteResponse;
import unitins.gestao.estoqueIA.dto.ia.ChatRequest;
import unitins.gestao.estoqueIA.dto.ia.ChatResponse;
import unitins.gestao.estoqueIA.dto.ia.MovimentacaoNlRequest;
import unitins.gestao.estoqueIA.dto.ia.MovimentacaoNlResponse;
import unitins.gestao.estoqueIA.service.ia.IaAgenteService;
import unitins.gestao.estoqueIA.service.ia.IaAssistenteService;

@RestController
@RequestMapping("/ia")
@RequiredArgsConstructor
public class IaController {

    private final IaAssistenteService service;
    private final IaAgenteService agente;

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

    /**
     * Agente com ferramentas: consulta o sistema (produtos, previsão, resumo,
     * curva ABC, anomalias, histórico) sob demanda para responder perguntas abertas.
     */
    @PostMapping("/agente")
    public ChatResponse agente(@RequestBody @Valid ChatRequest request) {
        return new ChatResponse(agente.conversar(request.mensagens()));
    }

    /** Resumo executivo do estoque em linguagem natural. */
    @GetMapping("/resumo")
    public AssistenteResponse resumo() {
        return new AssistenteResponse(service.resumoExecutivo());
    }

    /** Rascunho de pedido de compra a partir das reposições sugeridas. */
    @GetMapping("/pedido-compra")
    public AssistenteResponse pedidoCompra() {
        return new AssistenteResponse(service.gerarPedidoCompra());
    }

    /** Interpreta uma movimentação em linguagem natural (pré-visualização, não grava). */
    @PostMapping("/movimentacao-nl")
    public MovimentacaoNlResponse movimentacaoNl(@RequestBody @Valid MovimentacaoNlRequest request) {
        return service.interpretarMovimentacao(request.texto());
    }
}
