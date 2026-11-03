package br.com.oficina.execution.infrastructure.seguranca;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/** API sem sessão: tudo exige um JWT com papel ADMIN, exceto documentação e saúde. */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filtros(HttpSecurity http, JwtAuthenticationConverter conversor) throws Exception {
        http
                // CSRF só ameaça autenticação por cookie de sessão. Aqui não há sessão nem cookie: toda chamada
                // leva o JWT no cabeçalho Authorization, que o navegador não envia sozinho. Por isso é seguro desligar.
                .cors(Customizer.withDefaults()) // só para o painel de demonstração (ver corsConfigurationSource)
                .csrf(AbstractHttpConfigurer::disable) // NOSONAR java:S4502 - API stateless com Bearer token
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html",
                                "/swagger-ui/**").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(conversor)));
        return http.build();
    }

    /** Papéis vêm da claim "roles" do JWT (ADMIN do login, CLIENTE da Lambda de CPF, SERVICE entre serviços). */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter papeis = new JwtGrantedAuthoritiesConverter();
        papeis.setAuthoritiesClaimName("roles");
        papeis.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(papeis);
        return conversor;
    }

    /**
     * Deixa o painel do OS Service ({@code /saga-monitor.html}, outra origem) chamar a execução e o estoque
     * pelo navegador, na demonstração. Só as origens de {@code execution.cors.origens}; vazio desliga.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${execution.cors.origens:http://localhost:8081}") String origens) {
        List<String> permitidas = Arrays.stream(origens.split(",")).map(String::trim).filter(o -> !o.isEmpty()).toList();
        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        if (!permitidas.isEmpty()) {
            CorsConfiguration c = new CorsConfiguration();
            c.setAllowedOrigins(permitidas);
            c.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
            c.setAllowedHeaders(List.of("*")); // origem já restrita acima
            c.setMaxAge(600L);
            fonte.registerCorsConfiguration("/api/**", c);
        }
        return fonte;
    }
}
