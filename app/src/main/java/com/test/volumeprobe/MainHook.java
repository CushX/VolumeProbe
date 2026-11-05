package com.test.volumeprobe;

import android.util.Log;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Field;
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

        log(Log.INFO, TAG, "=== 准备拦截音量组件的构造函数 ===");

        try {
            ClassLoader cl = param.getDefaultClassLoader();
            
            // 我们要找的两个核心类
            String[] targetClasses = {
                "com.android.systemui.volume.VolumeUI",
                "com.android.systemui.volume.VolumeDialogComponent"
            };

            for (String className : targetClasses) {
                try {
                    Class<?> clazz = cl.loadClass(className);
                    // 拦截所有构造函数 <init>
                    for (Method method : clazz.getDeclaredMethods()) {
                        if (method.isConstructor()) {
                            hook(method).intercept(new ConstructorInspectHooker(className));
                            log(Log.INFO, TAG, "== 成功拦截构造函数: " + className + " ==");
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "挂载异常: " + t);
        }
    }

    private static class ConstructorInspectHooker implements XposedInterface.Hooker {
        private final String className;
        
        public ConstructorInspectHooker(String className) {
            this.className = className;
        }

        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            // 1. 放行构造函数，让对象正常创建
            Object result = chain.proceed();
            
            // 2. 获取刚刚创建出来的对象实例
            Object instance = chain.getThisObject();
            if (instance == null) return result;

            Log.d(TAG, "【查户口】发现实例创建完成: " + className);

            // 3. 暴力遍历它里面的所有成员变量 (Field)
            Class<?> clazz = instance.getClass();
            while (clazz != null && !clazz.equals(Object.class)) {
                for (Field field : clazz.getDeclaredFields()) {
                    try {
                        field.setAccessible(true);
                        Object value = field.get(instance);
                        
                        if (value != null) {
                            String typeName = value.getClass().getName();
                            // 过滤出名字包含 volume 或 dialog 的变量，这些很可能就是控制面板的实例
                            if (typeName.toLowerCase().contains("volume") || typeName.toLowerCase().contains("dialog")) {
                                Log.d(TAG, "  🎯【发现目标】变量名: " + field.getName() + " | 类型: " + typeName);
                            }
                        }
                    } catch (Throwable ignored) {}
                }
                clazz = clazz.getSuperclass();
            }
            return result;
        }
    }

    public MainHook() {}
}
