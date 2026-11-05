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

    // 这里保持静默，我们现在的目标是 SystemUI
    @Override
    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        super.onSystemServerStarting(param);
    }

    // 🌟 重点：由于作用域需要包含 SystemUI，我们要处理包的加载
    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        super.onPackageLoaded(param);
        String pkgName = param.getPackageName();
        
        // 我们只潜伏进系统 UI (音量面板所在的进程)
        if (!pkgName.equals("com.android.systemui")) {
            return;
        }

        log(Log.INFO, TAG, "=== 探针成功潜入系统 UI 进程 ===");

        try {
            // 尝试查找系统 UI 里的音量面板类 (基于 AOSP 源码标准命名)
            Class<?> volumeDialogClass = param.getClassLoader().loadClass("com.android.systemui.volume.VolumeDialogImpl");
            
            int hookedCount = 0;
            // 寻找面板显示的入口方法 (通常是 show 或 showH)
            for (Method method : volumeDialogClass.getDeclaredMethods()) {
                String methodName = method.getName();
                // 只要方法名是 show 或者 showH，统统挂载
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
            // 如果找不到类，会在这里报错
            log(Log.ERROR, TAG, "== SystemUI 探针挂载失败 (可能类名变了): " + t + " ==");
        }
    }

    private static class DialogShowHooker implements XposedInterface.Hooker {
        private final String name;
        public DialogShowHooker(String name) { this.name = name; }

        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            // 记录一下面板弹出的瞬间（加上防抖，避免刷屏）
            Log.d(TAG, "【UI探针】系统音量面板被呼出了！触发方法: " + name);
            
            // 我们在这里绝不修改 UI，直接放行，让系统自己画面板
            return chain.proceed();
        }
    }

    public MainHook() {}
}
