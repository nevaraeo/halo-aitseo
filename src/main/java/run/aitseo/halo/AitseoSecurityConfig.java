package run.aitseo.halo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;

/**
 * Permit anonymous access to /apis/aitseo.run/v1alpha1/** so that external
 * callers (AITSEO backend) can hit our controller without a Halo session
 * cookie. The controller itself enforces auth via the X-Connection-Key
 * header (see AitseoController.requireKey).
 *
 * Without this config, Halo's default SecurityWebFilterChain returns HTML
 * (redirect to console login) when there is no session cookie, which breaks
 * the JSON contract.
 *
 * Order < 0 to run before Halo's default chain.
 */
@Configuration
public class AitseoSecurityConfig {

    @Bean
    @Order(-100)
    public SecurityWebFilterChain aitseoApiSecurityFilterChain(ServerHttpSecurity http) {
        return http
            .securityMatcher(new PathPatternParserServerWebExchangeMatcher(
                "/apis/aitseo.run/v1alpha1/**"))
            .authorizeExchange(spec -> spec.anyExchange().permitAll())
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .logout(ServerHttpSecurity.LogoutSpec::disable)
            .build();
    }
}
