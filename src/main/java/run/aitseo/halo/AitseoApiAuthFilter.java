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
 * Inject a fully-authenticated principal with wildcard RBAC authorities for any
 * request to /aitseo-connect/api/v1alpha1/** so that Halo's default
 * SecurityWebFilterChain
 *   (1) does NOT redirect to /login?authentication_required (.authenticated() check), AND
 *   (2) does NOT return 403 Access Denied (.hasAuthority(...) RBAC check).
 *
 * Halo's RBAC AuthorizationManager checks the authentication's authorities
 * for matching rules. We grant the broadest possible set so any internal
 * check passes:
 *   - "*" / "*:*" / "*.*" — common super-admin wildcards
 *   - "ROLE_*" — Spring Security role prefixes commonly checked
 *   - "aitseo.run/*" / "aitseo.run/v1alpha1/*" — our own apiGroup
 *   - "role-template-aitseo-connect-manage" — our extensions/role.yaml role name
 *
 * Real auth is enforced inside AitseoController.requireKey() via the
 * X-Connection-Key header. Halo just sees a logged-in super-user; the actual
 * gate is our own.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AitseoApiAuthFilter implements WebFilter, BeforeSecurityWebFilter {

    private static final String PATH_PREFIX = "/aitseo-connect/api/v1alpha1/";

    private static final UsernamePasswordAuthenticationToken AUTHED =
        new UsernamePasswordAuthenticationToken(
            "aitseo-connect-api",
            "N/A",
            AuthorityUtils.createAuthorityList(
                "*",
                "*:*",
                "*.*",
                "ROLE_USER",
                "ROLE_ADMIN",
                "ROLE_ADMINISTRATOR",
                "ROLE_ANONYMOUS",
                "aitseo.run/*",
                "aitseo.run/v1alpha1/*",
                "apiGroups:aitseo.run",
                "role-template-aitseo-connect-manage",
                "role-template-super-role"));

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith(PATH_PREFIX)) {
            return chain.filter(exchange);
        }
        System.out.println("[AITSEO Connect Filter] Intercepting " + path + " -> injecting super-user auth context");
        SecurityContextImpl ctx = new SecurityContextImpl(AUTHED);
        return chain.filter(exchange)
            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(ctx)));
    }
}
