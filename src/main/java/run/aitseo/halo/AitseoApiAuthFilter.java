package run.aitseo.halo;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.csrf.CsrfWebFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import run.halo.app.security.BeforeSecurityWebFilter;

/**
 * For requests to /aitseo-connect/api/v1alpha1/**:
 *   1. Inject a fully-authenticated principal so any Halo security check
 *      that calls .authenticated() sees this request as logged-in.
 *   2. Set CsrfWebFilter.SHOULD_NOT_FILTER attribute so Spring Security's
 *      CSRF filter skips the request entirely (POST/PUT/DELETE from an
 *      external API caller doesn't have a CSRF token).
 *
 * Real auth is enforced inside AitseoController.requireKey() via the
 * X-Connection-Key header.
 *
 * Implements BOTH Spring's WebFilter (with @Order HIGHEST_PRECEDENCE) AND
 * Halo's BeforeSecurityWebFilter SPI for maximum compat.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AitseoApiAuthFilter implements WebFilter, BeforeSecurityWebFilter {

    private static final String PATH_PREFIX = "/aitseo-connect/api/v1alpha1/";

    private static final UsernamePasswordAuthenticationToken AUTHED =
        new UsernamePasswordAuthenticationToken(
            "aitseo-connect-api",
            "N/A",
            AuthorityUtils.createAuthorityList("ROLE_USER"));

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith(PATH_PREFIX)) {
            return chain.filter(exchange);
        }
        System.out.println("[AITSEO Connect Filter] " + exchange.getRequest().getMethod()
            + " " + path + " -> bypass CSRF + inject auth");
        // 跳过 Spring Security 的 CSRF 校验
        exchange.getAttributes().put(
            CsrfWebFilter.SHOULD_NOT_FILTER, Boolean.TRUE);
        SecurityContextImpl ctx = new SecurityContextImpl(AUTHED);
        return chain.filter(exchange)
            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(ctx)));
    }
}
