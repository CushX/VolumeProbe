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

        log(Log.INFO, TAG, "=== 探针潜入系统 UI 进程，准备查户口 ===");

        try {
            // 我们已经确认这个类存在
            Class<?> volumeUIClass = param.getDefaultClassLoader().loadClass("com.android.systemui.volume.VolumeUI");

            // Hook 它的 start 方法 (不加 before/after 注解，直接在 intercept 里控制)
            for (Method method : volumeUIClass.getDeclaredMethods()) {
                if (method.getName().equals("start")) {
                    hook(method).intercept(new StartAndInspectHooker());
                    log(Log.INFO, TAG, "== 成功挂载 VolumeUI.start()，等待开机触发 ==");
                }
            }
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "挂载异常: " + t);
        }
    }

    private static class StartAndInspectHooker implements XposedInterface.Hooker {
        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            // 1. 先让系统正常执行 start()
            Object result = chain.proceed();
            
            // 2. 拿到 VolumeUI 的当前实例
            Object volumeUIInstance = chain.getThisObject();
            if (volumeUIInstance == null) return result;

            Log.d(TAG, "【查户口】开始扫描 VolumeUI 内部的成员变量...");

            // 3. 暴力反射，遍历这个实例里的所有字段
            Class<?> clazz = volumeUIInstance.getClass();
            while (clazz != null && !clazz.equals(Object.class)) {
                for (Field field : clazz.getDeclaredFields()) {
                    try {
                        field.setAccessible(true);
                        Object value = field.get(volumeUIInstance);
                        if (value != null) {
                            String typeName = value.getClass().getName();
                            // 只要这个变量的类型名字里包含 volume 或者 dialog，大概率就是我们要找的面板
                            if (typeName.toLowerCase().contains("volume") || typeName.toLowerCase().contains("dialog")) {
                                Log.d(TAG, "【发现目标】变量名: " + field.getName() + " | 类型: " + typeName);
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
