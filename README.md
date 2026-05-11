# AITSEO Connect — Halo 插件

把 AITSEO 生成的 SEO 文章一键发布到 Halo 站点。跟 WordPress 的 [AITSEO Connect 插件](https://aitseo.com/wordpress-plugin) 配套设计,**使用方式完全一致**(粘连接密钥, 不需要 PAT)。

> 用 AITSEO Halo 集成的两种方式:
> - **PAT 方式** — Halo 自带的「个人令牌」, 无需装插件。在 AITSEO dashboard 直接填 URL + PAT 即可。
> - **插件方式** — 装这个插件, 用 connection key 验证, 跟 WordPress 体验一致, 还能配默认分类/标签等。

---

## 适配版本

- Halo: **2.20.0** 及以上
- JDK: 17+
- Gradle: 7.6+

---

## 构建

```bash
cd halo-plugin-aitseo
./gradlew clean build
```

JAR 输出到 `build/libs/aitseo-connect-1.0.0.jar`。

---

## 安装

1. 进 Halo 后台 → **「插件」** → **「安装」** → 上传刚才的 JAR
2. 找到「AITSEO Connect」, 点 **启用**
3. 启用后, 进 **「插件设置 → 基础配置」**
4. **Connection Key** 字段会自动填好一个 `swc_xxxxxxxx`(插件启动时随机生成); 复制这串
5. 可选: 配置默认分类、默认标签、是否立即发布

---

## 在 AITSEO 后台连接

1. 登录 AITSEO → **平台绑定** → **Halo CMS** 卡
2. 选「插件模式」(默认是 PAT 模式, 切换一下)
3. 填:
   - **站点 URL** — 例如 `https://your-halo-site.com`(不带末尾斜杠)
   - **Connection Key** — 粘上一步复制的 `swc_xxxx`
4. 点「连接」, AITSEO 会调 `GET /apis/aitseo.run/v1alpha1/site-info` 验证密钥

成功后, AITSEO 会显示你 Halo 站点的文章数、分类、标签。发布文章时选「Halo」作为目标平台即可。

---

## 提供的 REST 端点

所有端点都以 `https://your-halo-site.com/apis/aitseo.run/v1alpha1/` 开头, 所有请求必须带:

```
X-Connection-Key: swc_xxxxxxxxxxxxxxxx
```

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/site-info` | 验证 key + 返回插件版本 + 文章总数 |
| GET | `/categories` | 分类列表 (含 metadata.name / displayName / slug) |
| GET | `/tags` | 标签列表 |
| POST | `/publish` | 创建文章, 可选立即发布 |

**`/publish` 请求体:**

```json
{
  "title": "文章标题",
  "slug": "article-slug",
  "contentHtml": "<p>HTML 正文...</p>",
  "excerpt": "可选摘要",
  "categories": ["category-metadata-name"],
  "tags": ["tag-metadata-name"],
  "publish": true
}
```

`publish` 缺省时按插件设置的「自动发布」决定。`slug` 可选, 不填时自动从 title 生成。

---

## 重新生成 connection key

如果 key 泄露或想换新的, 进 **插件设置 → 基础配置**, 把 Connection Key 字段清空, **保存设置 + 重启 Halo**(或停用再启用插件)。`AitseoKeyBootstrap` 启动时检测到空 key 会再生成一个。

或者直接在该字段填一个你自己的 32+ 字符的密钥也行。

---

## 跟 WordPress 插件对应关系

| 功能 | WordPress (PHP) | Halo (本插件 Java) |
|------|-----------------|---------------------|
| 自动生成 connection_key | `on_activate` hook | `AitseoKeyBootstrap.ensureKey()` 启动事件 |
| 设置存储 | `wp_options` 表 | Halo `ConfigMap` extension |
| 自定义 REST 端点 | `register_rest_routes()` | `@RestController` + `@RequestMapping` |
| 认证 | `X-Connection-Key` 头校验 | `@RequestHeader("X-Connection-Key")` 校验 |
| 创建文章 | `wp_insert_post` | `client.create(Post)` 走 Extension API |
| 发布 | `wp_publish_post` | `spec.publish=true + publishTime` 后 update |

完全对齐, 用户从 WP 切到 Halo 不需要学新模型。

---

## 开发

修改源码后重新构建:

```bash
./gradlew build
```

热重载开发模式(需要 Halo plugin devtools):

```bash
./gradlew haloPlugin
```

详细 plugin 开发文档: [Halo 插件开发指南](https://docs.halo.run/developer-guide/plugin/introduction)

---

## 许可证

GPL-2.0 — 同 WordPress 插件。
