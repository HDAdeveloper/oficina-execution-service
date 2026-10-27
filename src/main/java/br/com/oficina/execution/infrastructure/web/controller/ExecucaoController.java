package br.com.oficina.execution.infrastructure.web.controller;

import br.com.oficina.execution.application.port.in.GestaoExecucoes;
import br.com.oficina.execution.infrastructure.web.dto.ExecucaoResponse;
import br.com.oficina.execution.infrastructure.web.dto.FalhaRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Execução dos serviços")
public class ExecucaoController {

    private final GestaoExecucoes execucoes;

    public ExecucaoController(GestaoExecucoes execucoes) {
        this.execucoes = execucoes;
    }

    @GetMapping("/api/execucoes")
    @Operation(summary = "Fila de trabalho: execuções na fila e em reparo, da mais antiga para a mais nova")
    public List<ExecucaoResponse> fila() {
        return execucoes.listarFila().stream().map(ExecucaoResponse::de).toList();
    }

    @GetMapping("/api/execucoes/{osId}")
    @Operation(summary = "Busca a execução de uma OS")
    public ExecucaoResponse buscar(@PathVariable Long osId) {
        return ExecucaoResponse.de(execucoes.buscar(osId));
    }

    @PostMapping("/api/execucoes/{osId}/iniciar")
    @Operation(summary = "Inicia o reparo (NA_FILA → EM_REPARO) e publica EXECUCAO_INICIADA")
    public ExecucaoResponse iniciar(@PathVariable Long osId) {
        return ExecucaoResponse.de(execucoes.iniciar(osId));
    }

    @PostMapping("/api/execucoes/{osId}/finalizar")
    @Operation(summary = "Finaliza o reparo (EM_REPARO → CONCLUIDA) e publica EXECUCAO_FINALIZADA")
    public ExecucaoResponse finalizar(@PathVariable Long osId) {
        return ExecucaoResponse.de(execucoes.finalizar(osId));
    }

    @PostMapping("/api/execucoes/{osId}/falhar")
    @Operation(summary = "Registra falha na execução e publica EXECUCAO_FALHOU (a Saga compensa)")
    public ExecucaoResponse falhar(@PathVariable Long osId, @RequestBody(required = false) FalhaRequest corpo) {
        return ExecucaoResponse.de(execucoes.falhar(osId, corpo == null ? null : corpo.motivo()));
    }
}
