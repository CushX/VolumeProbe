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
        log(Log.INFO, TAG, "=== 准备 Hook AudioService (API 102) ===");
        try {
            // 加载 AudioService 类
            Class<?> audioServiceClass = param.getClassLoader().loadClass("com.android.server.audio.AudioService");
            // 获取 setStreamVolume 方法
            Method setStreamVolumeMethod = audioServiceClass.getDeclaredMethod(
                    "setStreamVolume", int.class, int.class, int.class, String.class
            );
            
            // 安装 Hook
            hook(setStreamVolumeMethod).intercept(new SetStreamVolumeHooker());
            log(Log.INFO, TAG, "=== setStreamVolume Hook 安装成功 ===");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Hook 失败: " + t.toString());
        }
    }

    // Hooker 接口实现（去掉所有非必要的注解）
    private static class SetStreamVolumeHooker implements XposedInterface.Hooker {
        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            // 直接从 Chain 中获取参数
            int streamType = (int) chain.getArg(0);
            int index = (int) chain.getArg(1);

            if (streamType == 3) { // AudioManager.STREAM_MUSIC (媒体音量)
                Log.d(TAG, "【拦截成功】滑动媒体音量，目标值: " + index);
            }

            // 放行原始方法
            return chain.proceed();
        }
    }
}
