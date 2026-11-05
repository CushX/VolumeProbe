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
    }

    // 🌟 关键修改：将 onPackageLoaded 替换为 onPackageReady
    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        super.onPackageReady(param);
        String pkgName = param.getPackageName();
        
        if (!pkgName.equals("com.android.systemui")) {
            return;
        }

        log(Log.INFO, TAG, "=== 探针成功潜入系统 UI 进程 ===");

        try {
            // 使用 getDefaultClassLoader() 获取类加载器
            Class<?> volumeDialogClass = param.getDefaultClassLoader().loadClass("com.android.systemui.volume.VolumeDialogImpl");
            
            int hookedCount = 0;
            for (Method method : volumeDialogClass.getDeclaredMethods()) {
                String methodName = method.getName();
                if (methodName.equals("showH") || methodName.equals("show")) {
                    hook(method).intercept(new DialogShowHooker(methodName));
                    log(Log.INFO, TAG, "== 成功挂载音量面板方法: " + methodName + " ==");
                    hookedCount++;
                }
            }
            
            if (hookedCount == 0) {
                log(Log.ERROR, TAG, "== 没有找到 show 方法，可能类名或方法被混淆 ==");
            }

        } catch (Throwable t) {
            log(Log.ERROR, TAG, "== SystemUI 探针挂载失败 (可能类名变了): " + t + " ==");
        }
    }

    private static class DialogShowHooker implements XposedInterface.Hooker {
        private final String name;
        public DialogShowHooker(String name) { this.name = name; }

        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            Log.d(TAG, "【UI探针】系统音量面板被呼出了！触发方法: " + name);
            return chain.proceed();
        }
    }

    public MainHook() {}
}
