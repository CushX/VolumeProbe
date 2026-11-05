package com.test.volumeprobe;

import android.util.Log;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.ModuleLoadedParam;
import io.github.libxposed.api.SystemServerStartingParam;
import io.github.libxposed.api.annotations.BeforeInvoke;
import io.github.libxposed.api.annotations.XposedHooker;

public class MainHook extends XposedModule {
    private static final String TAG = "VolumeProbe";

    public MainHook() {
        this.setDebugLog(true);
    }

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        super.onModuleLoaded(param);
        log("=== 模块已加载到进程: " + param.getProcessName() + " ===");
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        super.onSystemServerStarting(param);
        log("=== 准备 Hook AudioService (API 102) ===");
        try {
            Class<?> audioServiceClass = param.getClassLoader().loadClass("com.android.server.audio.AudioService");
            
            this.hook(audioServiceClass, "setStreamVolume", 
                new Class[]{int.class, int.class, int.class, String.class}, 
                SetStreamVolumeHooker.class);
                
            log("=== setStreamVolume Hook 安装成功 ===");
        } catch (Throwable t) {
            log("Hook 失败: " + t.toString());
        }
    }

    @XposedHooker
    public static class SetStreamVolumeHooker implements XposedInterface.Hooker {
        @BeforeInvoke
        public static void before(XposedInterface.HookParam param) {
            Object[] args = param.getArgs();
            int streamType = (int) args[0];
            int index = (int) args[1];
            if (streamType == 3) {
                Log.d(TAG, "【拦截成功】滑动媒体音量，目标值: " + index);
            }
        }
    }
}
