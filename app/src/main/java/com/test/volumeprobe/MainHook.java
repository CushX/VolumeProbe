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
        log(Log.INFO, TAG, "=== 模块已加载 ===");
    }

    @Override
    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        super.onSystemServerStarting(param);
        log(Log.INFO, TAG, "=== 开始精准挂载 ===");
        try {
            Class<?> audioServiceClass = param.getClassLoader().loadClass("com.android.server.audio.AudioService");
            
            // 核心打击目标：这几个是系统和APP调音量的必经之路
            String[] targetMethods = {
                "setStreamVolume", 
                "adjustStreamVolume", 
                "adjustSuggestedStreamVolume"
            };

            for (Method method : audioServiceClass.getDeclaredMethods()) {
                if (java.util.Arrays.asList(targetMethods).contains(method.getName())) {
                    hook(method).intercept(new TargetVolumeHooker(method.getName()));
                    log(Log.INFO, TAG, "== 成功挂载: " + method.getName() + " ==");
                }
            }
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "异常: " + t);
        }
    }

    private static class TargetVolumeHooker implements XposedInterface.Hooker {
        private final String name;

        public TargetVolumeHooker(String name) {
            this.name = name;
        }

        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            try {
                // 使用框架自带的 log，一定能出现在 LSPosed 日志里！
                if (VolumeProbeLogger.getInstance() != null) {
                    VolumeProbeLogger.getInstance().log(Log.DEBUG, TAG, 
                        "【🔥触发】方法: " + name + " | 参数个数: " + chain.getArgs().size());
                }
            } catch (Throwable ignored) {
                // 哪怕打印日志报错，也不能影响系统正常的调音量
            }
            return chain.proceed();
        }
    }

    // 用于在静态类中获取当前的 Module 实例来打日志
    public static class VolumeProbeLogger {
        private static VolumeProbeLogger instance;
        public VolumeProbeLogger(XposedModule module) {}
        
        public static VolumeProbeLogger getInstance() { return instance; }
        
        public void log(int priority, String tag, String msg) {}
    }

    // 初始化 Logger
    public MainHook() {
        VolumeProbeLogger.instance = new VolumeProbeLogger(this) {
            @Override
            public void log(int priority, String tag, String msg) {
                MainHook.this.log(priority, tag, msg);
            }
        };
    }
}
