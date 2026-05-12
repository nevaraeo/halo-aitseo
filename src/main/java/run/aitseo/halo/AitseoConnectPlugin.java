package run.aitseo.halo;

import org.springframework.stereotype.Component;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

/**
 * AITSEO Connect - Halo plugin main entry.
 *
 * 极简版 (v1.0.20+): 仅声明插件生命周期, 不做任何 ConfigMap 读/写.
 *
 * 历史: v1.0.6 ~ v1.0.19 在 start() 里 ensureConnectionKey() 自动生成 swc_xxx
 * 写回 ConfigMap. 但 Halo 2.24 的 PluginStartedListener 同期也写该 ConfigMap,
 * 我们的写跟 Halo 的写撞 OptimisticLockingFailureException 死循环
 * (PluginStartedListener.java:94 .block() throws).
 *
 * 现在: 用户进「插件 → AITSEO Connect → 基础配置」自己填一个 32+ 字符的
 * swc_xxx 字符串保存即可. 任意随机 hex 都行, 关键是同一个串复制到 AITSEO
 * dashboard 的 connection key 字段. 不再自动生成.
 *
 * Controller (AitseoController) 读取 ConfigMap 的 connectionKey 做请求鉴权,
 * 用户填了就能 work.
 */
@Component
public class AitseoConnectPlugin extends BasePlugin {

    public AitseoConnectPlugin(PluginContext pluginContext) {
        super(pluginContext);
    }

    @Override
    public void start() {
        System.out.println("[AITSEO Connect] Plugin started. REST endpoints at /aitseo-connect/api/v1alpha1/*. "
            + "Set Connection Key in plugin settings before connecting from AITSEO dashboard.");
    }

    @Override
    public void stop() {
        System.out.println("[AITSEO Connect] Plugin stopped");
    }
}
