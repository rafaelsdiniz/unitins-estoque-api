package unitins.gestao.estoqueIA.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import unitins.gestao.estoqueIA.dto.analise.AnomaliaResponse;
import unitins.gestao.estoqueIA.dto.analise.CurvaAbcItem;
import unitins.gestao.estoqueIA.dto.analise.ResumoEstoque;
import unitins.gestao.estoqueIA.service.EstoqueAnaliseService;

import java.util.List;

@RestController
@RequestMapping("/analise")
@RequiredArgsConstructor
public class AnaliseController {

    private final EstoqueAnaliseService service;

    /** Visão executiva: totais, valor imobilizado e indicadores de risco. */
    @GetMapping("/resumo")
    public ResumoEstoque resumo() {
        return service.resumo();
    }

    /** Curva ABC por valor imobilizado em estoque. */
    @GetMapping("/curva-abc")
    public List<CurvaAbcItem> curvaAbc() {
        return service.curvaAbc();
    }

    /** Anomalias de consumo (picos de saída, estoque parado). */
    @GetMapping("/anomalias")
    public List<AnomaliaResponse> anomalias(@RequestParam(required = false) Integer janelaDias) {
        return service.anomalias(janelaDias);
    }
}
