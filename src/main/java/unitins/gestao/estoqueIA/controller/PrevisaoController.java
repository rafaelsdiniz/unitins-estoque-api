package unitins.gestao.estoqueIA.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import unitins.gestao.estoqueIA.dto.previsao.PrevisaoResponse;
import unitins.gestao.estoqueIA.service.PrevisaoService;

import java.util.List;

@RestController
@RequestMapping("/previsao")
@RequiredArgsConstructor
public class PrevisaoController {

    private final PrevisaoService service;

    @GetMapping("/produtos/{id}")
    public PrevisaoResponse prever(
            @PathVariable Long id,
            @RequestParam(required = false) Integer janelaDias
    ) {
        return service.prever(id, janelaDias);
    }

    @GetMapping("/reposicao-sugerida")
    public List<PrevisaoResponse> reposicaoSugerida(
            @RequestParam(required = false) Integer janelaDias
    ) {
        return service.reposicaoSugerida(janelaDias);
    }
}
