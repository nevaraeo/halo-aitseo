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
import run.halo.app.security.AdditionalWebFilter;

/**
 * Pre-populate an anonymous authentication for any request to /apis/aitseo.run/v1alpha1/**
 * BEFORE Halo's default security chain runs. This lets our REST controller handle
 * the request without Halo's auth filter redirecting unauthenticated requests to
 * the HTML login page.
 *
 * Auth is enforced by AitseoController.requireKey via X-Connection-Key header.
 *
 * Implements Halo's AdditionalWebFilter SPI (extends WebFilter + pf4j ExtensionPoint).
 */
@Component
public class AitseoAnonymousAccessFilter implements AdditionalWebFilter {

    private static final String PATH_PREFIX = "/apis/aitseo.run/v1alpha1/";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith(PATH_PREFIX)) {
            return chain.filter(exchange);
        }
        AnonymousAuthenticationToken anon = new AnonymousAuthenticationToken(
            "aitseo-connect",
            "aitseoApiUser",
            AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        SecurityContextImpl ctx = new SecurityContextImpl(anon);
        return chain.filter(exchange)
            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(ctx)));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
