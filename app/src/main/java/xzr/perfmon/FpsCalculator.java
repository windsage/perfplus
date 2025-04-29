package xzr.perfmon;

import android.view.Choreographer;

public class FpsCalculator {
    private long frameCount = 0;
    private long lastFrameTime = 0;
    private float fps = 0;
    private boolean isTracking = false;
    private static final int delay =
            SharedPreferencesUtil.sharedPreferences.getInt(SharedPreferencesUtil.REFRESHING_DELAY, SharedPreferencesUtil.DEFAULT_DELAY);

    /**
     * 开始跟踪FPS
     */
    public void startTracking() {
        if (isTracking) return;

        isTracking = true;
        frameCount = 0;
        lastFrameTime = 0;
        fps = 0;

        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (!isTracking) return;

                calcFps(frameTimeNanos);
                Choreographer.getInstance().postFrameCallback(this);
            }
        });
    }

    /**
     * 停止跟踪FPS
     */
    public void stopTracking() {
        isTracking = false;
    }

    /**
     * 计算当前帧率
     */
    private void calcFps(long frameTimeNanos) {
        frameCount++;

        if (lastFrameTime == 0) {
            lastFrameTime = frameTimeNanos;
            return;
        }

        float timeDiff = (frameTimeNanos - lastFrameTime) / 1000000.0f;

        // 每delay(ms)更新一次FPS
        if (timeDiff >= delay) {
            fps = (frameCount * 1000) / timeDiff;
            frameCount = 0;
            lastFrameTime = frameTimeNanos;
        }
    }

    /**
     * 获取当前FPS
     *
     * @return 当前帧率
     */
    public float getFps() {
        return fps;
    }

    /**
     * 获取当前FPS（取整）
     *
     * @return 当前帧率(整数)
     */
    public int getFpsInt() {
        return Math.round(fps);
    }
}
