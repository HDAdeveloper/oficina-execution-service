package br.com.oficina.execution.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Avisa, na partida, quando o serviço vai falar com a AWS de verdade sem que isso pareça intencional.
 *
 * <p>Em desenvolvimento o normal é {@code AWS_ENDPOINT_URL=http://localhost:4566} (LocalStack). Se a variável
 * ficar vazia fora de um ambiente de nuvem, o SDK usa a cadeia padrão de credenciais (por exemplo o
 * {@code AWS_PROFILE} do terminal) e passa a criar e consumir recursos na conta real.
 * Dentro do Kubernetes (EKS) o endpoint vazio é o esperado e nenhum aviso é emitido.
 */
public final class AvisoAwsReal {

    private static final Logger LOG = LoggerFactory.getLogger(AvisoAwsReal.class);

    private AvisoAwsReal() {
    }

    /** Verdadeiro quando não há endpoint próprio e o processo não está em um ambiente de nuvem. */
    static boolean deveAvisar(String endpoint, Map<String, String> ambiente) {
        boolean semEndpoint = endpoint == null || endpoint.isBlank();
        boolean emNuvem = ambiente.containsKey("KUBERNETES_SERVICE_HOST")
                || ambiente.containsKey("AWS_WEB_IDENTITY_TOKEN_FILE")
                || ambiente.containsKey("AWS_EXECUTION_ENV");
        return semEndpoint && !emNuvem;
    }

    public static void verificar(String endpoint) {
        verificar(endpoint, System.getenv(), LOG::warn);
    }

    static void verificar(String endpoint, Map<String, String> ambiente, Consumer<String> aviso) {
        if (!deveAvisar(endpoint, ambiente)) {
            return;
        }
        String perfil = ambiente.getOrDefault("AWS_PROFILE", "(nenhum; cadeia padrão de credenciais)");
        aviso.accept("""

                ==================================================================
                ATENCAO: AWS_ENDPOINT_URL esta vazio. Este servico vai usar a AWS REAL.
                Perfil/credenciais: %s
                Para desenvolver com o LocalStack, defina antes de iniciar:
                  PowerShell:  $env:AWS_ENDPOINT_URL = "http://localhost:4566"
                  Git Bash:    export AWS_ENDPOINT_URL=http://localhost:4566
                ==================================================================""".formatted(perfil));
    }
}
