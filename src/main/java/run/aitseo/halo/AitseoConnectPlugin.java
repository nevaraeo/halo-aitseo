package run.aitseo.halo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

@Component
public class AitseoConnectPlugin extends BasePlugin {

    private static final String CONFIGMAP_NAME = "aitseo-connect-configmap";
    private static final String BASIC_GROUP = "basic";
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
            .doOnError(err -> System.err.println(
                "[AITSEO Connect] Failed to ensure connection key: " + err.getMessage()))
            .onErrorResume(err -> Mono.empty())
            .subscribe();
    }

    @Override
    public void stop() {
        System.out.println("[AITSEO Connect] Plugin stopped");
    }

    private Mono<Void> ensureConnectionKey() {
        return client.fetch(ConfigMap.class, CONFIGMAP_NAME)
            .flatMap(cm -> {
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
                    System.out.println("[AITSEO Connect] Connection key already set, keeping existing value");
                    return Mono.empty();
                }
                String newKey = "swc_" + randomHex(32);
                basic.put("connectionKey", newKey);
                try {
                    data.put(BASIC_GROUP, objectMapper.writeValueAsString(basic));
                    cm.setData(data);
                    System.out.println("[AITSEO Connect] Auto-generated connection key (view it under Plugins > AITSEO Connect > Settings)");
                    return client.update(cm).then();
                } catch (JsonProcessingException e) {
                    return Mono.error(e);
                }
            })
            .switchIfEmpty(Mono.defer(() -> {
                System.out.println("[AITSEO Connect] ConfigMap not ready yet - will retry on next start");
                return Mono.empty();
            }));
    }

    private static String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        RNG.nextBytes(buf);
        StringBuilder sb = new StringBuilder(bytes * 2);
        for (byte b : buf) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
