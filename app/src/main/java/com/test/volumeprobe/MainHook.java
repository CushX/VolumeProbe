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

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        super.onPackageReady(param);
        String pkgName = param.getPackageName();
        
        if (!pkgName.equals("com.android.systemui")) {
            return;
        }

        log(Log.INFO, TAG, "=== 探针成功潜入系统 UI 进程 ===");

        // 候选类名列表
        String[] candidateClasses = {
            "com.android.systemui.volume.VolumeDialogImpl",
            "com.android.systemui.volume.VolumePanel",
            "com.android.systemui.volume.VolumeUI",
            "com.android.systemui.volume.VolumeDialog",
            "com.android.systemui.volume.VolumeDialogComponent"
        };

        ClassLoader classLoader = param.getDefaultClassLoader();
        boolean foundAny = false;

        for (String className : candidateClasses) {
            try {
                Class<?> clazz = findClassIfExists(className, classLoader);
                if (clazz == null) {
                    log(Log.DEBUG, TAG, "未找到类: " + className);
                    continue;
                }
                
                foundAny = true;
                log(Log.INFO, TAG, "✅ 找到类: " + className);
                
                int hookedCount = 0;
                for (Method method : clazz.getDeclaredMethods()) {
                    String methodName = method.getName();
                    if (methodName.equals("show") || methodName.equals("showH") || 
                        methodName.equals("initPanel") || methodName.equals("start")) {
                        hook(method).intercept(new DialogShowHooker(className, methodName));
                        log(Log.INFO, TAG, "  -> 成功挂载方法: " + methodName);
                        hookedCount++;
                    }
                }
                if (hookedCount == 0) {
                    log(Log.DEBUG, TAG, "  -> 类 " + className + " 中没有找到 show/initPanel/start 方法");
                }

            } catch (Throwable t) {
                log(Log.ERROR, TAG, "探测 " + className + " 时发生异常: " + t);
            }
        }

        if (!foundAny) {
            log(Log.ERROR, TAG, "❌ 所有候选类均未找到，音量面板可能被深度混淆或重构。");
        }
    }

    private Class<?> findClassIfExists(String className, ClassLoader classLoader) {
        try {
            return classLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static class DialogShowHooker implements XposedInterface.Hooker {
        private final String className;
        private final String methodName;
        public DialogShowHooker(String className, String methodName) {
            this.className = className;
            this.methodName = methodName;
        }

        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            Log.d(TAG, "【UI探针】音量面板被呼出！类: " + className + " 方法: " + methodName);
            return chain.proceed();
        }
    }

    public MainHook() {}
}
