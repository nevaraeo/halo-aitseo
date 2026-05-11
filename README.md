# AITSEO Connect — Halo 插件

[![Build JAR + Release](https://github.com/nevaraeo/halo-aitseo/actions/workflows/build-jar.yml/badge.svg)](https://github.com/nevaraeo/halo-aitseo/actions/workflows/build-jar.yml)
[![GitHub release](https://img.shields.io/github/v/release/nevaraeo/halo-aitseo?include_prereleases&label=latest%20jar)](https://github.com/nevaraeo/halo-aitseo/releases/latest)
[![License: GPL v2](https://img.shields.io/badge/License-GPL_v2-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.html)
[![Halo](https://img.shields.io/badge/Halo-%E2%89%A52.20-blue)](https://halo.run)

把 [AITSEO](https://aitseo.com) 生成的 SEO 文章一键发布到 [Halo](https://halo.run) 站点。跟 WordPress 的 AITSEO Connect 插件 **完全对等**的设计 — 装插件、复制 connection key、在 AITSEO 后台粘上,就完事了。

---

## 快速开始 (3 步)

### 1. 下载插件 JAR

无需自己编译。从 GitHub Release 直接拿:

👉 **<https://github.com/nevaraeo/halo-aitseo/releases/latest/download/aitseo-connect.jar>**

> 每次 push 到 main 分支, GitHub Actions 自动重新编译并替换 `latest` tag 下的 `aitseo-connect.jar`。

### 2. 装到 Halo 站点

1. 进 Halo 后台 → **「插件」** → **「安装」** → 上传 `aitseo-connect.jar`
2. 找到 **AITSEO Connect**, 点 **启用**
3. 进 **「AITSEO Connect → 设置 → 基础配置」**
4. **Connection Key** 字段会自动填好 `swc_xxxxxxxx...` (插件首次启动随机生成 32 字节 hex), 复制这串

### 3. 在 AITSEO dashboard 连接

1. 登录 [AITSEO](https://aitseo.com) → **平台绑定** → 找到 **Halo CMS** 卡片
2. 填:
   - **Halo 站点地址** — 例如 `https://your-halo-site.com`
   - **Connection Key** — 粘第 2 步复制的 `swc_xxxx`
3. 点 **连接 Halo**

连接成功后, dashboard 会展示 Halo 站点的文章数、分类、标签。生成文章后选 Halo 作为发布目标,自动同步过去。

---

## 适配版本

| 组件 | 要求 |
|------|------|
| Halo | ≥ 2.20.0 |
| JDK | 17+ |
| Gradle (开发用) | 8.x (Halo plugin 0.4.0 不兼容 Gradle 9.x) |

---

## REST 端点

所有端点 base path: `https://your-halo-site.com/apis/aitseo.run/v1alpha1/`

所有请求必须带 header:

```
X-Connection-Key: swc_xxxxxxxxxxxxxxxx
```

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/site-info` | 验证 key + 返回插件版本 + 文章总数 |
| GET | `/categories` | 分类列表 (含 metadata.name / displayName / slug) |
| GET | `/tags` | 标签列表 |
| POST | `/publish` | 创建文章, 可选立即发布 |

**`POST /publish` 请求体:**

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

- `slug` 可选, 不填时自动从 title 生成
- `categories` / `tags` 用 Halo 分类/标签的 **metadata.name** 列表 (内部 ID, 不是 displayName)
- `publish` 不传时按插件设置的「自动发布」决定 (默认 true)

**响应:**

```json
{
  "ok": true,
  "post_name": "aitseo-1736789012345-a1b2c3",
  "status": "published",
  "permalink": "/archives/article-slug"
}
```

---

## 重新生成 Connection Key

如果 key 泄露或想换:

1. **插件设置 → 基础配置** → 把 Connection Key 字段清空 → **保存**
2. **停用再启用插件** (或重启 Halo)
3. 插件启动时检测到空 key, 会自动生成新的 `swc_xxxx`

或直接在该字段填你自己的 32+ 字符字符串也行。

---

## 自己编译 (可选)

> 一般用户走 Release 下 jar 就够了, 这步只给想改源码的人。

```bash
git clone https://github.com/nevaraeo/halo-aitseo.git
cd halo-aitseo
./gradlew clean build -x test
```

JAR 输出: `build/libs/aitseo-connect-1.0.3.jar`

热重载开发 (需 Halo 本地实例):

```bash
./gradlew haloPlugin
```

详细 plugin 开发文档: [Halo 插件开发指南](https://docs.halo.run/developer-guide/plugin/introduction)

---

## 架构 / 实现说明

```
External caller (AITSEO backend)
        │
        │  Authorization: X-Connection-Key: swc_xxxx
        ▼
┌────────────────────────────────────────────┐
│  Halo (Spring WebFlux)                     │
│                                            │
│  ┌──────────────────────────────────────┐  │
│  │  AitseoAnonymousAccessFilter         │  │  ← Halo SPI
│  │  (AdditionalWebFilter, Order=HIGH)   │  │    pre-populate
│  │  对 /apis/aitseo.run/v1alpha1/**     │  │    anonymous auth
│  │  写入 AnonymousAuthenticationToken   │  │    skip HTML
│  │  让 Halo 主 SecurityChain 放行       │  │    login redirect
│  └──────────────────────────────────────┘  │
│                  │                         │
│                  ▼                         │
│  ┌──────────────────────────────────────┐  │
│  │  AitseoController                    │  │  ← 真实鉴权
│  │  @RestController                     │  │    比对
│  │  requireKey() 比对 ConfigMap.basic   │  │    X-Connection-Key
│  │  .connectionKey vs X-Connection-Key  │  │    跟存储的 key
│  └──────────────────────────────────────┘  │
│                  │                         │
│                  ▼                         │
│  ┌──────────────────────────────────────┐  │
│  │  ReactiveExtensionClient             │  │  ← Halo Extension API
│  │  client.create(Post) / list(...) ... │  │    (Post / Category /
│  └──────────────────────────────────────┘  │    Tag / ConfigMap)
│                                            │
└────────────────────────────────────────────┘
```

**关键设计点:**

- **`AitseoConnectPlugin.start()`** — 插件启动钩子。读 `aitseo-connect-configmap`, 如果 `basic.connectionKey` 为空就生成 `swc_+32-byte-hex` 写回。等价于 WordPress 的 `register_activation_hook`。
- **`AitseoAnonymousAccessFilter`** — 实现 Halo SPI `run.halo.app.security.AdditionalWebFilter`。Halo 默认 SecurityWebFilterChain 把无 session cookie 请求重定向到 HTML 登录页, 这就会让 AITSEO 后端解析 JSON 时报 `Unexpected token <`。此 filter 在 `/apis/aitseo.run/**` 路径预先写入匿名 auth context, 绕过这个重定向。
- **`AitseoController.requireKey`** — 真鉴权点。从 ConfigMap 拿存储的 key, 跟请求头比对。空 key / 不匹配返 401 JSON。

---

## 跟 WordPress 插件对应关系

| 功能 | WordPress (PHP) | Halo (本插件 Java) |
|------|-----------------|---------------------|
| 自动生成 connection_key | `register_activation_hook` | `AitseoConnectPlugin.start()` |
| 设置存储 | `wp_options` 表 | Halo `ConfigMap` extension |
| 自定义 REST 端点 | `register_rest_routes()` | `@RestController` + `@RequestMapping` |
| 头校验 | `X-Connection-Key` 比 option 值 | `AitseoController.requireKey` |
| 创建文章 | `wp_insert_post` | `ReactiveExtensionClient.create(Post)` |
| 发布 | `wp_publish_post` | `spec.publish = true` 后 update |
| 绕过默认 auth | (无需,REST 默认 public) | `AdditionalWebFilter` 写 anon auth |

完全对齐, 用户从 WP 切到 Halo 不需要重新学。

---

## 发布流程 (维护者用)

1. 改源码
2. **bump 版本号** (从 1.0.1 起严格递增, 同时改两处):
   - `build.gradle` → `version = 'X.Y.Z'`
   - `src/main/resources/plugin.yaml` → `spec.version: X.Y.Z`
3. `git commit && git push origin main`
4. GitHub Actions (`.github/workflows/build-jar.yml`) 自动:
   - 用 Gradle 8.5 + JDK 17 编译
   - 跑 `gradle clean build -x test`
   - 把 `build/libs/aitseo-connect-X.Y.Z.jar` 拷成 `aitseo-connect.jar`
   - 替换 `latest` tag 的 release attachment
5. 用户点 Halo 后台「检查更新」, plugin.yaml 里的 semver 比当前大就显示更新提示

> **重要**: build.gradle 跟 plugin.yaml 的 version 必须严格一致, 不一致会让构建产物名和 plugin 元数据不匹配。

---

## License

[GPL-2.0](LICENSE) — 同上游 Halo, 跟 WordPress 版插件一致。
