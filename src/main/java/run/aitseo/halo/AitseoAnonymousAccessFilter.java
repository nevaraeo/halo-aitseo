package run.aitseo.halo;

import org.springframework.core.Ordered;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import run.halo.app.security.BeforeSecurityWebFilter;

/**
 * Pre-populate an anonymous authentication for any request to /apis/aitseo.run/v1alpha1/**
 * BEFORE Halo's default security chain runs.
 *
 * Why: Halo's default SecurityWebFilterChain redirects unauthenticated requests to
 * the HTML console login page, breaking the JSON contract for external API callers
 * (e.g. AITSEO backend calling our plugin endpoints with X-Connection-Key).
 *
 * Real auth is enforced inside AitseoController.requireKey() by comparing the
 * X-Connection-Key header against the value stored in the plugin's ConfigMap.
 *
 * Uses Halo SPI run.halo.app.security.BeforeSecurityWebFilter so that this filter
 * is registered into the chain BEFORE Halo's main security filter chain (the
 * generic AdditionalWebFilter SPI does not guarantee ordering relative to
 * Halo's security).
 */
@Component
public class AitseoAnonymousAccessFilter implements BeforeSecurityWebFilter {

    private static final String PATH_PREFIX = "/apis/aitseo.run/v1alpha1/";

    private static final AnonymousAuthenticationToken ANON =
        new AnonymousAuthenticationToken(
            "aitseo-connect",
            "aitseoApiUser",
            AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith(PATH_PREFIX)) {
            return chain.filter(exchange);
        }
        SecurityContextImpl ctx = new SecurityContextImpl(ANON);
        return chain.filter(exchange)
            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(ctx)));
    }

    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
