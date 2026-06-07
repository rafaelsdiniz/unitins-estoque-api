package unitins.gestao.estoqueIA.dto.ia;

import unitins.gestao.estoqueIA.entity.enums.TipoMovimentacao;

/**
 * Interpretação de uma movimentação descrita em linguagem natural.
 *
 * É apenas uma PRÉ-VISUALIZAÇÃO: a IA estrutura a intenção (tipo, quantidade,
 * produto), mas nada é gravado. O usuário confirma e o cliente chama
 * POST /movimentacoes com {produtoId, tipo, quantidade} para efetivar.
 */
public record MovimentacaoNlResponse(
        boolean interpretado,
        Long produtoId,
        String produtoNome,
        TipoMovimentacao tipo,
        Integer quantidade,
        String mensagem
) {
}
