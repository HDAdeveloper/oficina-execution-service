package br.com.oficina.execution.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AvisoAwsRealTest {

    @Test
    void avisaQuandoNaoHaEndpointENemNuvem() {
        List<String> avisos = new ArrayList<>();

        AvisoAwsReal.verificar("", Map.of("AWS_PROFILE", "oficina"), avisos::add);

        assertThat(avisos).singleElement().satisfies(m -> assertThat(m)
                .contains("AWS REAL").contains("oficina").contains("http://localhost:4566"));
    }

    @Test
    void avisaTambemQuandoEndpointEhNulo() {
        List<String> avisos = new ArrayList<>();

        AvisoAwsReal.verificar(null, Map.of(), avisos::add);

        assertThat(avisos).singleElement().satisfies(m -> assertThat(m).contains("cadeia padr"));
    }

    @Test
    void naoAvisaComEndpointDoLocalStack() {
        List<String> avisos = new ArrayList<>();

        AvisoAwsReal.verificar("http://localhost:4566", Map.of(), avisos::add);

        assertThat(avisos).isEmpty();
    }

    @Test
    void naoAvisaDentroDoKubernetes() {
        List<String> avisos = new ArrayList<>();

        AvisoAwsReal.verificar("", Map.of("KUBERNETES_SERVICE_HOST", "10.0.0.1"), avisos::add);
        AvisoAwsReal.verificar("", Map.of("AWS_WEB_IDENTITY_TOKEN_FILE", "/var/run/token"), avisos::add);
        AvisoAwsReal.verificar("", Map.of("AWS_EXECUTION_ENV", "AWS_Lambda_java21"), avisos::add);

        assertThat(avisos).isEmpty();
    }

    @Test
    void verificarSemParametrosNaoLancaExcecao() {
        assertThatCode(() -> AvisoAwsReal.verificar("http://localhost:4566")).doesNotThrowAnyException();
    }
}
