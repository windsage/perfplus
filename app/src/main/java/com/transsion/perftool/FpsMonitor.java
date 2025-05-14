package com.transsion.perftool;

import android.content.Context;
import android.view.Display;
import android.view.WindowManager;

import java.util.Locale;

public class FpsMonitor {
    private static FpsMonitor instance;
    private final FpsCalculator fpsCalculator;

    private FpsMonitor() {
        fpsCalculator = new FpsCalculator();
    }

    public static synchronized FpsMonitor getInstance() {
        if (instance == null) {
            instance = new FpsMonitor();
        }
        return instance;
    }

    public void start() {
        fpsCalculator.startTracking();
    }

    public void stop() {
        fpsCalculator.stopTracking();
    }

    public float getFps() {
        return fpsCalculator.getFps();
    }

    /**
     * 创建与原JNI函数兼容的返回格式
     */
    public String getFpsgoInfo() {
        Context context = App.getContext();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        int targetFps = Math.round(display.getRefreshRate());

        float currentFps = fpsCalculator.getFps();
        String formattedFps = String.format(Locale.US,"%.2f", currentFps);

        return targetFps + "_" + formattedFps;
    }
}
