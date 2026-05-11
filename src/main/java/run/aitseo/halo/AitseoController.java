package run.aitseo.halo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Category;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.extension.content.Tag;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ReactiveSettingFetcher;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AITSEO Connect — REST endpoints under /aitseo-connect/api/v1alpha1/
 *
 * 所有 mutation endpoint 都用 X-Connection-Key header 验证, 跟 WP 插件设计完全对齐。
 * 这样 AITSEO 后端只需要存 connection_key (跟 WordPress 一样的 UX), 不需要 PAT。
 *
 * Endpoints:
 *   GET  /aitseo-connect/api/v1alpha1/site-info        — 验证 key + 返回站点基本信息
 *   GET  /aitseo-connect/api/v1alpha1/categories       — 分类列表
 *   GET  /aitseo-connect/api/v1alpha1/tags             — 标签列表
 *   POST /aitseo-connect/api/v1alpha1/publish          — 创建文章 (可选立即发布)
 *
 * 注: 用 Spring WebFlux reactive 模式。所有方法返回 Mono<T>。
 */
@RestController
@RequestMapping("/aitseo-connect/api/v1alpha1")
public class AitseoController {

    private final ReactiveExtensionClient client;
    private final ReactiveSettingFetcher settingFetcher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public AitseoController(ReactiveExtensionClient client, ReactiveSettingFetcher settingFetcher) {
        this.client = client;
        this.settingFetcher = settingFetcher;
    }

    // ════════════════════════════════════════════════════════════════
    // X-Connection-Key 验证
    // ════════════════════════════════════════════════════════════════

    private Mono<AitseoSettings> getSettings() {
        return settingFetcher.fetch("basic", AitseoSettings.class)
            .defaultIfEmpty(new AitseoSettings());
    }

    /** 验证 connection_key, 不匹配返 401 */
    private Mono<Void> requireKey(String providedKey) {
        return getSettings().flatMap(s -> {
            String expected = s.getConnectionKey();
            if (expected == null || expected.isEmpty()) {
                return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "插件未初始化 connection key — 请到 Halo 后台「插件 → AITSEO Connect → 设置」重新生成"));
            }
            if (!expected.equals(providedKey)) {
                return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "connection key 不匹配"));
            }
            return Mono.empty();
        });
    }

    // ════════════════════════════════════════════════════════════════
    // GET /site-info
    // ════════════════════════════════════════════════════════════════
    @GetMapping("/site-info")
    public Mono<Map<String, Object>> siteInfo(
            @RequestHeader(value = "X-Connection-Key", required = false) String key) {
        return requireKey(key).then(
            client.list(Post.class, p -> true, null, 0, 1)
                .map(page -> {
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("plugin", "aitseo-connect");
                    resp.put("plugin_version", "1.0.0");
                    resp.put("halo_compatible", "2.20+");
                    resp.put("post_count", page.getTotal());
                    resp.put("timestamp", Instant.now().toString());
                    return resp;
                })
        );
    }

    // ════════════════════════════════════════════════════════════════
    // GET /categories
    // ════════════════════════════════════════════════════════════════
    @GetMapping("/categories")
    public Mono<Map<String, Object>> categories(
            @RequestHeader(value = "X-Connection-Key", required = false) String key) {
        return requireKey(key).then(
            client.list(Category.class, c -> true, null)
                .map(c -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("name", c.getMetadata().getName());
                    m.put("displayName", c.getSpec() != null ? c.getSpec().getDisplayName() : "");
                    m.put("slug", c.getSpec() != null ? c.getSpec().getSlug() : "");
                    return m;
                })
                .collectList()
                .map(items -> {
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("items", items);
                    resp.put("total", items.size());
                    return resp;
                })
        );
    }

    // ════════════════════════════════════════════════════════════════
    // GET /tags
    // ════════════════════════════════════════════════════════════════
    @GetMapping("/tags")
    public Mono<Map<String, Object>> tags(
            @RequestHeader(value = "X-Connection-Key", required = false) String key) {
        return requireKey(key).then(
            client.list(Tag.class, t -> true, null)
                .map(t -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("name", t.getMetadata().getName());
                    m.put("displayName", t.getSpec() != null ? t.getSpec().getDisplayName() : "");
                    m.put("slug", t.getSpec() != null ? t.getSpec().getSlug() : "");
                    return m;
                })
                .collectList()
                .map(items -> {
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("items", items);
                    resp.put("total", items.size());
                    return resp;
                })
        );
    }

    // ════════════════════════════════════════════════════════════════
    // POST /publish
    // body: { title, slug?, contentHtml, excerpt?, categories?, tags?, publish? }
    // ════════════════════════════════════════════════════════════════
    @PostMapping(value = "/publish", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> publishPost(
            @RequestHeader(value = "X-Connection-Key", required = false) String key,
            @RequestBody PublishRequest body) {

        if (body == null || body.title == null || body.title.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "title 必填"));
        }
        if (body.contentHtml == null || body.contentHtml.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "contentHtml 必填"));
        }

        return requireKey(key)
            .then(getSettings())
            .flatMap(settings -> {
                boolean publish = body.publish != null ? body.publish : settings.shouldPublishImmediately();
                String postName = "aitseo-" + System.currentTimeMillis() + "-"
                    + UUID.randomUUID().toString().substring(0, 6);
                String slug = (body.slug != null && !body.slug.isBlank())
                    ? body.slug
                    : slugify(body.title);

                List<String> finalCategories = (body.categories != null && !body.categories.isEmpty())
                    ? body.categories
                    : parseCsv(settings.getDefaultCategory());
                List<String> finalTags = (body.tags != null && !body.tags.isEmpty())
                    ? body.tags
                    : parseCsv(settings.getDefaultTags());

                Post post = new Post();
                Metadata metadata = new Metadata();
                metadata.setName(postName);

                Map<String, String> annotations = new HashMap<>();
                Map<String, Object> contentJson = new HashMap<>();
                contentJson.put("raw", body.contentHtml);
                contentJson.put("content", body.contentHtml);
                contentJson.put("rawType", "html");
                try {
                    annotations.put("content.halo.run/content-json", objectMapper.writeValueAsString(contentJson));
                } catch (Exception e) {
                    return Mono.error(new RuntimeException("content-json 序列化失败: " + e.getMessage()));
                }
                metadata.setAnnotations(annotations);
                post.setMetadata(metadata);

                Post.PostSpec spec = new Post.PostSpec();
                spec.setTitle(body.title);
                spec.setSlug(slug);
                spec.setDeleted(false);
                spec.setPublish(false);
                spec.setPinned(false);
                spec.setAllowComment(true);
                spec.setVisible(Post.VisibleEnum.PUBLIC);
                spec.setPriority(0);

                Post.Excerpt excerpt = new Post.Excerpt();
                if (body.excerpt != null && !body.excerpt.isBlank()) {
                    excerpt.setRaw(body.excerpt);
                    excerpt.setAutoGenerate(false);
                } else {
                    excerpt.setRaw("");
                    excerpt.setAutoGenerate(true);
                }
                spec.setExcerpt(excerpt);
                spec.setCategories(finalCategories);
                spec.setTags(finalTags);
                post.setSpec(spec);

                return client.create(post)
                    .flatMap(created -> {
                        if (!publish) {
                            return Mono.just(buildResp(created.getMetadata().getName(), "draft", null));
                        }
                        created.getSpec().setPublish(true);
                        created.getSpec().setPublishTime(Instant.now());
                        return client.update(created)
                            .map(pub -> {
                                String permalink = pub.getStatus() != null ? pub.getStatus().getPermalink() : null;
                                return buildResp(pub.getMetadata().getName(), "published", permalink);
                            });
                    });
            });
    }

    private Map<String, Object> buildResp(String name, String status, String permalink) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", true);
        resp.put("post_name", name);
        resp.put("status", status);
        if (permalink != null) resp.put("permalink", permalink);
        return resp;
    }

    private List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }

    private String slugify(String title) {
        String s = title.toLowerCase()
            .replaceAll("[^a-z0-9\\s-]", "")
            .trim()
            .replaceAll("\\s+", "-");
        if (s.length() >= 3 && s.length() <= 60) return s;
        if (s.length() > 60) return s.substring(0, 60);
        return "post-" + System.currentTimeMillis();
    }

    public static class PublishRequest {
        public String title;
        public String slug;
        public String contentHtml;
        public String excerpt;
        public List<String> categories;
        public List<String> tags;
        public Boolean publish;
    }
}
