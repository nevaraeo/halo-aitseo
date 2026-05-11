package run.aitseo.halo;

import org.springframework.stereotype.Component;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

/**
 * AITSEO Connect — Halo 插件主入口。
 *
 * 跟 WordPress 的 AITSEO Connect 插件设计一致:
 *   - 激活时自动生成连接密钥 (Connection Key), 存到 settings 里
 *   - 暴露 REST endpoints (/apis/aitseo.run/v1alpha1/*) 用 X-Connection-Key 头验证
 *   - 用户在 Halo 后台 "插件 → AITSEO Connect → 设置" 里看密钥, 粘到 AITSEO dashboard
 *
 * 跟 Halo 官方 UC API (PAT 方式) 对比:
 *   - PAT 路线: 用户去「个人中心 → 个人令牌」生成, 给 AITSEO 用
 *   - 本插件路线: 装插件 → 激活自动生成 connection_key, 像 WordPress 一样, 用户体验更熟
 *
 * 两种方式我们后端都支持 (lib/halo.ts), 用户挑一种用就行。
 */
@Component
public class AitseoConnectPlugin extends BasePlugin {

    public AitseoConnectPlugin(PluginContext pluginContext) {
        super(pluginContext);
    }

    @Override
    public void start() {
        System.out.println("[AITSEO Connect] 插件已启动 — 端点已就绪 (/apis/aitseo.run/v1alpha1/*)");
    }

    @Override
    public void stop() {
        System.out.println("[AITSEO Connect] 插件已停用");
    }
}
