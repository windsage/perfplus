package com.transsion.perftool;

import android.content.Context;
import android.view.Display;
import android.view.WindowManager;

import java.util.Locale;

public class FpsMonitor {
    private static FpsMonitor instance;
    private final FpsCalculator fpsCalculator;
    private final PlatformUtil platformUtil;

    private FpsMonitor() {
        fpsCalculator = new FpsCalculator();
        platformUtil = PlatformUtil.getInstance();
    }

    public static synchronized FpsMonitor getInstance() {
        if (instance == null) {
            instance = new FpsMonitor();
        }
        return instance;
    }

    public void start() {
        if (!platformUtil.isQualcomm() && !platformUtil.isMediaTek()) {
            fpsCalculator.startTracking();
        }
    }

    public void stop() {
        if (!platformUtil.isQualcomm() && !platformUtil.isMediaTek()) {
            fpsCalculator.stopTracking();
        }
    }

    /**
     * Get current FPS value
     * For Qualcomm: read from DRM node
     * For MediaTek: read from fpsgo node
     * For others: use Choreographer calculation
     */
    public float getFps() {
        if (platformUtil.isQualcomm()) {
            float fps = JniTools.getQcomDisplayFps();
            return (fps > 0) ? fps : 0.0f;
        } else if (platformUtil.isMediaTek()) {
            float fps = JniTools.getMtkDisplayFps();
            return (fps > 0) ? fps : 0.0f;
        } else {
            return fpsCalculator.getFps();
        }
    }

    /**
     * Get target refresh rate from display
     */
    private int getTargetRefreshRate() {
        Context context = App.getContext();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        return Math.round(display.getRefreshRate());
    }

    /**
     * Create FPS info string compatible with original format
     * Format: "targetFps_realFps"
     * Example: "120_80.5"
     */
    public String getFpsgoInfo() {
        int targetFps = getTargetRefreshRate();
        float currentFps = getFps();
        String formattedFps = String.format(Locale.US, "%.1f", currentFps);

        return targetFps + "_" + formattedFps;
    }
}