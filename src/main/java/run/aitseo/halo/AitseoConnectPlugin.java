package run.aitseo.halo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * AITSEO Connect - Halo plugin entry.
 *
 * On plugin start:
 *   1. Try to fetch ConfigMap aitseo-connect-configmap (Halo creates this
 *      lazily from extensions/configmap.yaml; may not exist immediately)
 *   2. Retry every 2 seconds for up to 60 seconds if not found yet
 *   3. Once found, if basic.connectionKey is empty, generate
 *      swc_+32-byte-hex and write back via client.update()
 *
 * This is the equivalent of WordPress register_activation_hook.
 */
@Component
public class AitseoConnectPlugin extends BasePlugin {

    private static final String CONFIGMAP_NAME = "aitseo-connect-configmap";
    private static final String BASIC_GROUP = "basic";
    private static final int MAX_FETCH_RETRIES = 30;
    private static final Duration FETCH_RETRY_DELAY = Duration.ofSeconds(2);
    private static final SecureRandom RNG = new SecureRandom();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ReactiveExtensionClient client;

    public AitseoConnectPlugin(PluginContext pluginContext, ReactiveExtensionClient client) {
        super(pluginContext);
        this.client = client;
    }

    @Override
    public void start() {
        System.out.println("[AITSEO Connect] Plugin starting - REST endpoints ready at /apis/aitseo.run/v1alpha1/*");
        ensureConnectionKey()
            .subscribe(
                unused -> {},
                err -> System.err.println("[AITSEO Connect] ensureConnectionKey terminal error: " + err.getMessage())
            );
    }

    @Override
    public void stop() {
        System.out.println("[AITSEO Connect] Plugin stopped");
    }

    /**
     * Fetch ConfigMap with retries (Halo creates it lazily from extensions/).
     * Once obtained, check basic.connectionKey; if empty, generate a key and
     * update the ConfigMap.
     */
    private Mono<Void> ensureConnectionKey() {
        // 启动后延迟 3s 避开 Halo 自身 apply extensions/*.yaml 导致的并发写;
        // 整个 fetch+update 链包在 Mono.defer 里, 每次 retry 都重新 fetch
        // 拿到最新版本号; OptimisticLockingFailure (Halo r2dbc 在 PluginStartedListener
        // 期间并发改 ConfigMap version 引起) 时 retry 5 次每次 1s 间隔.
        return Mono.delay(Duration.ofSeconds(3))
            .then(Mono.defer(() -> fetchConfigMapWithRetry().flatMap(this::generateKeyIfMissing)))
            .retryWhen(Retry.fixedDelay(5, Duration.ofSeconds(1))
                .filter(t -> t instanceof OptimisticLockingFailureException
                    || (t.getCause() != null && t.getCause() instanceof OptimisticLockingFailureException))
                .doBeforeRetry(sig -> System.out.println(
                    "[AITSEO Connect] ConfigMap version conflict, retry "
                        + (sig.totalRetries() + 1) + "/5")))
            .onErrorResume(err -> {
                System.err.println("[AITSEO Connect] ensureConnectionKey failed: " + err.getMessage());
                return Mono.empty();
            });
    }

    private Mono<ConfigMap> fetchConfigMapWithRetry() {
        return client.fetch(ConfigMap.class, CONFIGMAP_NAME)
            .switchIfEmpty(Mono.defer(() -> {
                System.out.println("[AITSEO Connect] ConfigMap not ready, will retry...");
                return Mono.error(new IllegalStateException("ConfigMap not ready"));
            }))
            .retryWhen(Retry.fixedDelay(MAX_FETCH_RETRIES, FETCH_RETRY_DELAY)
                .filter(t -> t instanceof IllegalStateException)
                .doBeforeRetry(sig -> System.out.println(
                    "[AITSEO Connect] retry " + (sig.totalRetries() + 1)
                        + "/" + MAX_FETCH_RETRIES + " fetch ConfigMap")));
    }

    private Mono<Void> generateKeyIfMissing(ConfigMap cm) {
        Map<String, String> data = cm.getData();
        if (data == null) data = new HashMap<>();
        String basicJson = data.getOrDefault(BASIC_GROUP, "{}");
        ObjectNode basic;
        try {
            basic = (ObjectNode) objectMapper.readTree(basicJson);
        } catch (Exception e) {
            basic = objectMapper.createObjectNode();
        }
        String existing = basic.hasNonNull("connectionKey")
            ? basic.get("connectionKey").asText()
            : "";
        if (existing != null && !existing.isBlank()) {
            System.out.println("[AITSEO Connect] connectionKey already set (" + existing.substring(0, Math.min(8, existing.length())) + "...), keeping");
            return Mono.empty();
        }
        String newKey = "swc_" + randomHex(32);
        basic.put("connectionKey", newKey);
        try {
            data.put(BASIC_GROUP, objectMapper.writeValueAsString(basic));
            cm.setData(data);
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
        return client.update(cm)
            .doOnSuccess(updated -> System.out.println(
                "[AITSEO Connect] Auto-generated connection key. Check 'Plugins -> AITSEO Connect -> Settings' to copy it."))
            .then();
    }

    private static String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        RNG.nextBytes(buf);
        StringBuilder sb = new StringBuilder(bytes * 2);
        for (byte b : buf) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
