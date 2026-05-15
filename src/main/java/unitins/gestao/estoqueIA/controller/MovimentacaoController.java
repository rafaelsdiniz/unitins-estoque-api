package unitins.gestao.estoqueIA.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import unitins.gestao.estoqueIA.dto.movimentacao.MovimentacaoRequest;
import unitins.gestao.estoqueIA.dto.movimentacao.MovimentacaoResponse;
import unitins.gestao.estoqueIA.service.MovimentacaoService;

@RestController
@RequestMapping("/movimentacoes")
@RequiredArgsConstructor
public class MovimentacaoController {

    private final MovimentacaoService service;

    @GetMapping
    public Page<MovimentacaoResponse> listarPorProduto(
            @RequestParam Long produtoId,
            Pageable pageable
    ) {
        return service.listarPorProduto(produtoId, pageable);
    }

    @PostMapping
    public ResponseEntity<MovimentacaoResponse> registrar(@RequestBody @Valid MovimentacaoRequest request) {
        MovimentacaoResponse registrada = service.registrar(request);
        return ResponseEntity.status(201).body(registrada);
    }
}
