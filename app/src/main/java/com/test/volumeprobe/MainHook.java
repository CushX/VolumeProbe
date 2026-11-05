package com.test.volumeprobe;

import android.util.Log;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.ModuleLoadedParam;
import io.github.libxposed.api.SystemServerStartingParam;
import java.lang.reflect.Method;

public class MainHook extends XposedModule {
    private static final String TAG = "VolumeProbe";

    public MainHook() {
        this.setDebugLog(true);
    }

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        super.onModuleLoaded(param);
        log(Log.INFO, TAG, "=== 模块已加载到进程: " + param.getProcessName() + " ===");
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        super.onSystemServerStarting(param);
        log(Log.INFO, TAG, "=== 准备 Hook AudioService (API 102) ===");
        try {
            Class<?> audioServiceClass = param.getClassLoader().loadClass("com.android.server.audio.AudioService");
            Method setStreamVolumeMethod = audioServiceClass.getDeclaredMethod(
                    "setStreamVolume", int.class, int.class, int.class, String.class
            );
            
            hook(setStreamVolumeMethod).intercept(new SetStreamVolumeHooker());
                
            log(Log.INFO, TAG, "=== setStreamVolume Hook 安装成功 ===");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Hook 失败: " + t.toString());
        }
    }

    private static class SetStreamVolumeHooker implements XposedInterface.Hooker {
        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            Object[] args = chain.getArgs().toArray();
            int streamType = (int) args[0];
            int index = (int) args[1];
            
            if (streamType == 3) { // STREAM_MUSIC
                Log.d(TAG, "【拦截成功】滑动媒体音量，目标值: " + index);
            }
            
            return chain.proceed();
        }
    }
}
