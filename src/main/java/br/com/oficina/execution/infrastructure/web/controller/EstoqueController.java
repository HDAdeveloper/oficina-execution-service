package br.com.oficina.execution.infrastructure.web.controller;

import br.com.oficina.execution.application.port.in.GestaoEstoque;
import br.com.oficina.execution.infrastructure.web.dto.EstoqueRequests.NovaPeca;
import br.com.oficina.execution.infrastructure.web.dto.EstoqueRequests.Reposicao;
import br.com.oficina.execution.infrastructure.web.dto.ItemEstoqueResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Estoque de peças")
public class EstoqueController {

    private final GestaoEstoque estoque;

    public EstoqueController(GestaoEstoque estoque) {
        this.estoque = estoque;
    }

    @GetMapping("/api/estoque")
    @Operation(summary = "Lista as peças em estoque (ordem de código)")
    public List<ItemEstoqueResponse> listar() {
        return estoque.listar().stream().map(ItemEstoqueResponse::de).toList();
    }

    @GetMapping("/api/estoque/{codigo}")
    @Operation(summary = "Busca uma peça pelo código")
    public ItemEstoqueResponse buscar(@PathVariable String codigo) {
        return ItemEstoqueResponse.de(estoque.buscar(codigo));
    }

    @PostMapping("/api/estoque")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra uma peça (409 se o código já existe)")
    public ItemEstoqueResponse cadastrar(@RequestBody NovaPeca corpo) {
        return ItemEstoqueResponse.de(estoque.cadastrar(corpo.codigo(), corpo.nome(), corpo.quantidade()));
    }

    @PostMapping("/api/estoque/{codigo}/reposicoes")
    @Operation(summary = "Soma unidades ao estoque de uma peça")
    public ItemEstoqueResponse repor(@PathVariable String codigo, @RequestBody Reposicao corpo) {
        return ItemEstoqueResponse.de(estoque.repor(codigo, corpo.quantidade()));
    }
}
