package com.test.volumeprobe;

import android.util.Log;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Method;
import java.util.List;

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
        try {
            Class<?> audioServiceClass = param.getClassLoader().loadClass("com.android.server.audio.AudioService");
            String[] targetMethods = {"setStreamVolume", "adjustStreamVolume", "adjustSuggestedStreamVolume"};

            for (Method method : audioServiceClass.getDeclaredMethods()) {
                if (java.util.Arrays.asList(targetMethods).contains(method.getName())) {
                    hook(method).intercept(new TargetVolumeHooker(method.getName()));
                }
            }
            log(Log.INFO, TAG, "=== 终极参数探针部署完毕 ===");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "异常: " + t);
        }
    }

    private static class TargetVolumeHooker implements XposedInterface.Hooker {
        private final String name;
        private static long lastLogTime = 0;

        public TargetVolumeHooker(String name) {
            this.name = name;
        }

        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            List<Object> args = chain.getArgs();
            
            // 找寻参数中有没有 "3"（代表媒体音量）
            boolean isMedia = false;
            for (Object arg : args) {
                if (arg instanceof Integer && (Integer)arg == 3) {
                    isMedia = true; break;
                }
            }
            
            // 只有媒体音量，并且距离上次打印超过 500 毫秒才打印，防止日志刷屏
            if (isMedia) {
                long now = System.currentTimeMillis();
                if (now - lastLogTime > 500) {
                    lastLogTime = now;
                    
                    StringBuilder sb = new StringBuilder("【🔥触发】" + name + " 的所有参数: \n");
                    for (int i = 0; i < args.size(); i++) {
                        Object arg = args.get(i);
                        String type = arg == null ? "null" : arg.getClass().getSimpleName();
                        sb.append(" 参数[").append(i).append("] (").append(type).append(") = ").append(arg).append("\n");
                    }
                    
                    // 顺便尝试打印一下到底是哪个包名发起的
                    if (VolumeProbeLogger.getInstance() != null) {
                        VolumeProbeLogger.getInstance().log(Log.DEBUG, TAG, sb.toString());
                    }
                }
            }
            return chain.proceed();
        }
    }

    public static class VolumeProbeLogger {
        private static VolumeProbeLogger instance;
        public VolumeProbeLogger(XposedModule module) {}
        public static VolumeProbeLogger getInstance() { return instance; }
        public void log(int priority, String tag, String msg) {}
    }

    public MainHook() {
        VolumeProbeLogger.instance = new VolumeProbeLogger(this) {
            @Override
            public void log(int priority, String tag, String msg) {
                MainHook.this.log(priority, tag, msg);
            }
        };
    }
}
