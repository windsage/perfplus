package xzr.perfmon;

import java.util.ArrayList;
import java.util.List;

public class PlatformUtil {
    public enum PlatformType {
        QUALCOMM,
        MEDIATEK,
        UNISOC,
        UNKNOWN
    }

    private static PlatformUtil instance;
    private PlatformType platformType = PlatformType.UNKNOWN; // 默认为未知
    private boolean isInitialized = false;
    private final Object lock = new Object();
    private List<PlatformInitCallback> callbackList = new ArrayList<>();

    public interface PlatformInitCallback {
        void onPlatformDetected(PlatformType platformType);
    }

    private PlatformUtil() {
    }

    public static synchronized PlatformUtil getInstance() {
        if (instance == null) {
            instance = new PlatformUtil();
        }
        return instance;
    }

    public void initAsync(final PlatformInitCallback callback) {
        if (isInitialized) {
            if (callback != null) {
                callback.onPlatformDetected(platformType);
            }
            return;
        }

        if (callback != null) {
            synchronized (callbackList) {
                callbackList.add(callback);
            }
        }

        synchronized (lock) {
            if (!isInitialized) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        detectPlatform();

                        synchronized (callbackList) {
                            for (PlatformInitCallback cb : callbackList) {
                                cb.onPlatformDetected(platformType);
                            }
                            callbackList.clear();
                        }
                    }
                }, "PlatformDetection").start();
            }
        }
    }

    public PlatformType getPlatformType() {
        return platformType;
    }

    public boolean isQualcomm() {
        return platformType == PlatformType.QUALCOMM;
    }

    public boolean isMediaTek() {
        return platformType == PlatformType.MEDIATEK;
    }

    public boolean isUnisoc() {
        return platformType == PlatformType.UNISOC;
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    private void detectPlatform() {
        String hardware = getProp("ro.hardware");
        String soc = getProp("ro.soc.manufacturer");
        String platform = getProp("ro.board.platform");

        // 检查Qualcomm平台关键字
        if (containsIgnoreCase(hardware, "qcom") ||
                containsIgnoreCase(platform, "qcom") ||
                containsIgnoreCase(hardware, "qualcomm") ||
                containsIgnoreCase(soc, "qualcomm")) {
            platformType = PlatformType.QUALCOMM;
        }
        // 检查MediaTek平台关键字
        else if (containsIgnoreCase(hardware, "mt") ||
                containsIgnoreCase(platform, "mt") ||
                containsIgnoreCase(hardware, "mediatek") ||
                containsIgnoreCase(soc, "mediatek")) {
            platformType = PlatformType.MEDIATEK;
        }
        // 检查Unisoc平台关键字
        else if (containsIgnoreCase(hardware, "sprd") ||
                containsIgnoreCase(platform, "sprd") ||
                containsIgnoreCase(hardware, "spreadtrum") ||
                containsIgnoreCase(hardware, "unisoc") ||
                containsIgnoreCase(soc, "unisoc") ||
                containsIgnoreCase(soc, "spreadtrum")) {
            platformType = PlatformType.UNISOC;
        }
        else {
            platformType = PlatformType.UNKNOWN;
        }

        // 标记为已初始化
        synchronized (lock) {
            isInitialized = true;
        }
    }

    private String getProp(String key) {
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            return (String) systemProperties.getMethod("get", String.class).invoke(null, key);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private boolean containsIgnoreCase(String source, String target) {
        return source != null && target != null &&
                source.toLowerCase().contains(target.toLowerCase());
    }
}