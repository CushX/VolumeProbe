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
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        super.onPackageReady(param);
        if (!param.getPackageName().equals("com.android.systemui")) return;

        log(Log.INFO, TAG, "=== 准备拦截真正的音量面板 ===");

        try {
            ClassLoader cl = param.getDefaultClassLoader();
            
            // 🌟 就是你截图里发现的真神！
            String targetClassName = "com.android.systemui.volume.dialog.VolumeDialog";
            
            Class<?> clazz = cl.loadClass(targetClassName);
            log(Log.INFO, TAG, "✅ 成功找到类: " + targetClassName);

            int count = 0;
            // 遍历它的所有方法，看看它到底有哪些显示/隐藏的方法
            for (Method method : clazz.getDeclaredMethods()) {
                String methodName = method.getName();
                
                // 通常音量面板的显示方法叫 show 或者 showH
                if (methodName.equals("show") || methodName.equals("showH")) {
                    hook(method).intercept(new DialogHooker(methodName));
                    log(Log.INFO, TAG, "== 成功挂载方法: " + methodName + " ==");
                    count++;
                }
            }
            
            if (count == 0) {
                // 如果没找到 show，我们就把所有方法名打印出来看看
                log(Log.DEBUG, TAG, "没找到 show 方法，这个类的方法有:");
                for (Method m : clazz.getDeclaredMethods()) {
                    log(Log.DEBUG, TAG, "  -> " + m.getName());
                }
            }

        } catch (Throwable t) {
            log(Log.ERROR, TAG, "挂载异常: " + t);
        }
    }

    private static class DialogHooker implements XposedInterface.Hooker {
        private final String methodName;
        public DialogHooker(String methodName) { this.methodName = methodName; }

        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            Log.d(TAG, "🔥【UI探针】音量面板被呼出了！方法: " + methodName);
            
            // 这里我们不修改任何东西，直接放行
            return chain.proceed();
        }
    }

    public MainHook() {}
}
