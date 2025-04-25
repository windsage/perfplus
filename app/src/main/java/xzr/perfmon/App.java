package xzr.perfmon;

import android.app.Application;
import android.util.Log;

public class App extends Application {
    private static final String TAG = "PerfMon";
    @Override
    public void onCreate() {
        super.onCreate();
        PlatformUtil.getInstance().initAsync(new PlatformUtil.PlatformInitCallback() {
            @Override
            public void onPlatformDetected(PlatformUtil.PlatformType platformType) {
                Log.d(TAG, "Platform detected async: " + platformType);

                switch (platformType) {
                    case QUALCOMM:
                        Log.d(TAG, "Initializing Qualcomm specific components");
                        break;
                    case MEDIATEK:
                        Log.d(TAG, "Initializing MediaTek specific components");
                        break;
                    case UNISOC:
                        Log.d(TAG, "Initializing Unisoc specific components");
                        break;
                    default:
                        Log.d(TAG, "Initializing generic components");
                        break;
                }
            }
        });
    }
}
