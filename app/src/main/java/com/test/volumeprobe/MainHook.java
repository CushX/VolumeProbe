package com.test.volumeprobe;

import android.util.Log;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Method;

public class MainHook extends XposedModule {
    private static final String TAG = "VolumeProbe";

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        super.onModuleLoaded(param);
        log(Log.INFO, TAG, "=== 模块已加载到进程: " + param.getProcessName() + " ===");
    }

    @Override
    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        super.onSystemServerStarting(param);
        log(Log.INFO, TAG, "=== 开启【天罗地网】全量 Hook 模式 ===");
        try {
            Class<?> audioServiceClass = param.getClassLoader().loadClass("com.android.server.audio.AudioService");
            int count = 0;
            
            // 遍历 AudioService 里的所有方法
            for (Method method : audioServiceClass.getDeclaredMethods()) {
                String methodName = method.getName();
                // 只要方法名包含 Volume，统统 Hook 住！
                if (methodName.contains("Volume")) {
                    try {
                        hook(method).intercept(new UniversalVolumeHooker(methodName));
                        count++;
                        log(Log.INFO, TAG, "== 成功挂载方法: " + methodName + " ==");
                    } catch (Throwable t) {
                        log(Log.ERROR, TAG, "挂载 " + methodName + " 失败: " + t);
                    }
                }
            }
            log(Log.INFO, TAG, "=== 天罗地网部署完毕，共挂了 " + count + " 个方法 ===");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "核心异常: " + t.toString());
        }
    }

    // 万能拦截器
    private static class UniversalVolumeHooker implements XposedInterface.Hooker {
        private final String methodName;

        public UniversalVolumeHooker(String methodName) {
            this.methodName = methodName;
        }

        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            // 把当前执行的方法名和参数全打印出来
            StringBuilder sb = new StringBuilder("【捕获】方法: " + methodName + " | 参数: ");
            for (Object arg : chain.getArgs()) {
                sb.append(arg).append(", ");
            }
            Log.d(TAG, sb.toString());
            
            return chain.proceed();
        }
    }
}
