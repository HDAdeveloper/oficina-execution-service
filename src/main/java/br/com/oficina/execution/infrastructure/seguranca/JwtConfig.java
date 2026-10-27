package br.com.oficina.execution.infrastructure.seguranca;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/** Validação de tokens JWT (HS256) com o segredo compartilhado com o OS Service. */
@Configuration
@EnableConfigurationProperties(SegurancaProperties.class)
public class JwtConfig {

    @Bean
    public SecretKey chaveJwt(SegurancaProperties props) {
        byte[] bytes = props.jwtSecret() == null ? new byte[0] : props.jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "oficina.seguranca.jwt-secret precisa ter pelo menos 32 caracteres (variável JWT_SECRET)");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey chaveJwt) {
        return NimbusJwtDecoder.withSecretKey(chaveJwt).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
