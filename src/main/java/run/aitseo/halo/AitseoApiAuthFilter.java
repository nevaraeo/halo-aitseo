package run.aitseo.halo;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import run.halo.app.security.BeforeSecurityWebFilter;

/**
 * Pre-populate a FULLY-AUTHENTICATED principal for any request to
 * /apis/aitseo.run/v1alpha1/** so that Halo's default SecurityWebFilterChain
 * does NOT redirect to /login?authentication_required.
 *
 * Implements BOTH Spring's WebFilter (with @Order HIGHEST_PRECEDENCE) AND
 * Halo's BeforeSecurityWebFilter SPI. The dual approach maximises the chance
 * that the filter is picked up regardless of how Halo's plugin loader
 * registers WebFilter beans.
 *
 * Real auth is enforced inside AitseoController.requireKey() by comparing the
 * X-Connection-Key header against the value stored in the plugin ConfigMap.
 *
 * Diagnostic: prints to stdout on every match so v1.0.5 install can be
 * verified via Halo backend logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AitseoApiAuthFilter implements WebFilter, BeforeSecurityWebFilter {

    private static final String PATH_PREFIX = "/apis/aitseo.run/v1alpha1/";

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
        System.out.println("[AITSEO Connect Filter] Intercepting " + path + " -> injecting auth context");
        SecurityContextImpl ctx = new SecurityContextImpl(AUTHED);
        return chain.filter(exchange)
            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(ctx)));
    }
}
