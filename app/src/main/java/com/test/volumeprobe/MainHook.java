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
            Class<?> audioServiceClass = param.getClassLoader().loadClass("com.android.server.audio.AudioService");

            // 第一道门：setStreamVolume (通常拖拽滑块触发)
            Method setStreamVolumeMethod = audioServiceClass.getDeclaredMethod(
                    "setStreamVolume", int.class, int.class, int.class, String.class);
            hook(setStreamVolumeMethod).intercept(new SetStreamVolumeHooker());
            log(Log.INFO, TAG, "=== setStreamVolume Hook 安装成功 ===");

            // 第二道门：adjustStreamVolume (通常按音量键触发)
            Method adjustStreamVolumeMethod = audioServiceClass.getDeclaredMethod(
                    "adjustStreamVolume", int.class, int.class, int.class, String.class);
            hook(adjustStreamVolumeMethod).intercept(new AdjustStreamVolumeHooker());
            log(Log.INFO, TAG, "=== adjustStreamVolume Hook 安装成功 ===");

        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Hook 失败: " + t.toString());
        }
    }

    // 监听拖拽
    private static class SetStreamVolumeHooker implements XposedInterface.Hooker {
        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            int streamType = (int) chain.getArg(0);
            int index = (int) chain.getArg(1);
            // 不限制类型，全部打印！看看到底走的什么流
            Log.d(TAG, "【拦截 set】流类型: " + streamType + ", 目标值: " + index);
            return chain.proceed();
        }
    }

    // 监听按键
    private static class AdjustStreamVolumeHooker implements XposedInterface.Hooker {
        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            int streamType = (int) chain.getArg(0);
            int direction = (int) chain.getArg(1);
            // 不限制类型，全部打印！
            Log.d(TAG, "【拦截 adjust】流类型: " + streamType + ", 方向: " + direction);
            return chain.proceed();
        }
    }
}
