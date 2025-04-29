package xzr.perfmon;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.util.Log;

public class App extends Application {
    private static final String TAG = "PerfMon";
    @SuppressLint("StaticFieldLeak")
    private static Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        SharedPreferencesUtil.init(this);
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
        FpsMonitor.getInstance().start();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        FpsMonitor.getInstance().stop();
    }

    public static Context getContext() {
        return context;
    }
}
