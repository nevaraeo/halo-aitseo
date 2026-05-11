package run.aitseo.halo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.ConfigMap;
import run.halo.app.extension.ReactiveExtensionClient;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 插件启动时, 检查 basic.connectionKey 是否为空。空的话生成一个 swc_+random hex,
 * 写回 ConfigMap (aitseo-connect-configmap)。
 *
 * 跟 WordPress 插件「on_activate 生成 connection_key」完全等价。
 */
@Component
public class AitseoKeyBootstrap {

    private static final String CONFIGMAP_NAME = "aitseo-connect-configmap";
    private static final String BASIC_GROUP = "basic";
    private static final SecureRandom RNG = new SecureRandom();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ReactiveExtensionClient client;

    @Autowired
    public AitseoKeyBootstrap(ReactiveExtensionClient client) {
        this.client = client;
    }

    @EventListener(ApplicationReadyEvent.class)
    public Mono<Void> ensureKey() {
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
                    // 已有 key, 不动
                    return Mono.empty();
                }
                String newKey = "swc_" + randomHex(32);
                basic.put("connectionKey", newKey);
                try {
                    data.put(BASIC_GROUP, objectMapper.writeValueAsString(basic));
                    cm.setData(data);
                    System.out.println("[AITSEO Connect] 已自动生成 connection key (查看在 插件设置 → 基础配置)");
                    return client.update(cm).then();
                } catch (JsonProcessingException e) {
                    return Mono.error(e);
                }
            })
            .switchIfEmpty(Mono.defer(() -> {
                // ConfigMap 还没创建出来时 Halo 会自己建; 等下次启动再生成
                System.out.println("[AITSEO Connect] ConfigMap 尚未就绪, 下次启动再生成 connection key");
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
