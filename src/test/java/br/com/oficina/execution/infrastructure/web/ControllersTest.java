package br.com.oficina.execution.infrastructure.web;

import br.com.oficina.execution.application.port.in.GestaoEstoque;
import br.com.oficina.execution.application.port.in.GestaoExecucoes;
import br.com.oficina.execution.domain.exception.ConflitoException;
import br.com.oficina.execution.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.execution.domain.exception.RegraDeNegocioException;
import br.com.oficina.execution.domain.model.Execucao;
import br.com.oficina.execution.domain.model.ItemEstoque;
import br.com.oficina.execution.domain.model.PecaReservada;
import br.com.oficina.execution.infrastructure.seguranca.JwtConfig;
import br.com.oficina.execution.infrastructure.seguranca.SecurityConfig;
import br.com.oficina.execution.infrastructure.web.controller.EstoqueController;
import br.com.oficina.execution.infrastructure.web.controller.ExecucaoController;
import br.com.oficina.execution.infrastructure.web.erro.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({EstoqueController.class, ExecucaoController.class})
@Import({SecurityConfig.class, JwtConfig.class, ApiExceptionHandler.class})
class ControllersTest {

    private static final Instant T0 = Instant.parse("2026-10-23T12:00:00Z");

    @Autowired MockMvc mvc;
    @MockitoBean GestaoEstoque estoque;
    @MockitoBean GestaoExecucoes execucoes;

    private static Execucao execucao() {
        return Execucao.nova(42L, "saga-42", "corr-42", "res-1", List.of(new PecaReservada("PEC-1", 2)),
                List.of("Revisão"), T0);
    }

    // ------------------------------------------------------------ segurança

    private void preflightLiberado(String caminho) throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options(caminho)
                        .header("Origin", "http://localhost:8081")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Access-Control-Allow-Origin", "http://localhost:8081"));
    }

    @Test
    void painelDoOsServicePodeChamarAExecucaoPeloNavegador() throws Exception {
        preflightLiberado("/api/execucoes/42/falhar");
    }

    @Test
    void painelDoOsServicePodeCadastrarEstoquePeloNavegador() throws Exception {
        preflightLiberado("/api/estoque");
    }

    @Test
    void outraOrigemNaoRecebeLiberacao() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/estoque")
                        .header("Origin", "http://site-qualquer.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void semTokenDa401EmTodasAsRotas() throws Exception {
        mvc.perform(get("/api/estoque")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/estoque/PEC-1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/estoque").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/execucoes")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/execucoes/42/iniciar")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ estoque

    @Test
    void listaEBuscaPecas() throws Exception {
        when(estoque.listar()).thenReturn(List.of(new ItemEstoque("PEC-1", "Filtro", 5)));
        when(estoque.buscar("pec-1")).thenReturn(new ItemEstoque("PEC-1", "Filtro", 5));

        mvc.perform(get("/api/estoque").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value("PEC-1"))
                .andExpect(jsonPath("$[0].disponivel").value(5));
        mvc.perform(get("/api/estoque/pec-1").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Filtro"));
    }

    @Test
    void pecaInexistenteDa404EmProblemJson() throws Exception {
        when(estoque.buscar("X")).thenThrow(new RecursoNaoEncontradoException("Peça não encontrada no estoque: X"));

        mvc.perform(get("/api/estoque/X").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Peça não encontrada no estoque: X"));
    }

    @Test
    void cadastraPecaComStatus201() throws Exception {
        when(estoque.cadastrar("PEC-9", "Pastilha", 3)).thenReturn(new ItemEstoque("PEC-9", "Pastilha", 3));

        mvc.perform(post("/api/estoque").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"PEC-9\",\"nome\":\"Pastilha\",\"quantidade\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("PEC-9"))
                .andExpect(jsonPath("$.disponivel").value(3));
    }

    @Test
    void codigoRepetidoDa409() throws Exception {
        when(estoque.cadastrar(any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenThrow(new ConflitoException("A peça PEC-9 já existe"));

        mvc.perform(post("/api/estoque").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"PEC-9\",\"nome\":\"Pastilha\",\"quantidade\":3}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflito"));
    }

    @Test
    void reposicaoInvalidaDa422() throws Exception {
        when(estoque.repor("PEC-1", 0)).thenThrow(new RegraDeNegocioException("A quantidade a repor deve ser maior que zero"));

        mvc.perform(post("/api/estoque/PEC-1/reposicoes").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantidade\":0}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void reposicaoDevolveOSaldoNovo() throws Exception {
        when(estoque.repor("PEC-1", 4)).thenReturn(new ItemEstoque("PEC-1", "Filtro", 9));

        mvc.perform(post("/api/estoque/PEC-1/reposicoes").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantidade\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponivel").value(9));
    }

    @Test
    void jsonQuebradoDa400() throws Exception {
        mvc.perform(post("/api/estoque").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ execuções

    @Test
    void listaAFilaEBuscaPorOs() throws Exception {
        when(execucoes.listarFila()).thenReturn(List.of(execucao()));
        when(execucoes.buscar(42L)).thenReturn(execucao());

        mvc.perform(get("/api/execucoes").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].osId").value(42))
                .andExpect(jsonPath("$[0].status").value("NA_FILA"))
                .andExpect(jsonPath("$[0].pecas[0].codigo").value("PEC-1"))
                .andExpect(jsonPath("$[0].servicos[0]").value("Revisão"));
        mvc.perform(get("/api/execucoes/42").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservaId").value("res-1"));
    }

    @Test
    void iniciaEFinalizaChamandoOCasoDeUso() throws Exception {
        Execucao e = execucao();
        e.iniciar(T0.plusSeconds(1));
        when(execucoes.iniciar(42L)).thenReturn(e);
        when(execucoes.finalizar(42L)).thenReturn(e);

        mvc.perform(post("/api/execucoes/42/iniciar").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_REPARO"));
        mvc.perform(post("/api/execucoes/42/finalizar").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isOk());
        verify(execucoes).finalizar(42L);
    }

    @Test
    void falhaLeOMotivoEPermiteCorpoVazio() throws Exception {
        Execucao e = execucao();
        e.falhar("motor travou", T0.plusSeconds(1));
        when(execucoes.falhar(42L, "motor travou")).thenReturn(e);
        when(execucoes.falhar(42L, null)).thenReturn(e);

        mvc.perform(post("/api/execucoes/42/falhar").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"motor travou\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FALHOU"))
                .andExpect(jsonPath("$.motivoFalha").value("motor travou"));
        mvc.perform(post("/api/execucoes/42/falhar").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isOk());
        verify(execucoes).falhar(42L, null);
    }

    @Test
    void transicaoInvalidaDa422EOsInexistenteDa404() throws Exception {
        when(execucoes.finalizar(42L)).thenThrow(new RegraDeNegocioException("está NA_FILA e não pode ser finalizada"));
        when(execucoes.buscar(7L)).thenThrow(new RecursoNaoEncontradoException("Execução da OS 7 não encontrada"));

        mvc.perform(post("/api/execucoes/42/finalizar").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/execucoes/7").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isNotFound());
    }

    @Test
    void erroInesperadoDa500SemVazarDetalhes() throws Exception {
        when(execucoes.listarFila()).thenThrow(new IllegalStateException("segredo interno"));

        mvc.perform(get("/api/execucoes").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("segredo"))));
    }
}
