package apincer.android.mmate.utils;

import android.util.Log;

public class Utils {
    private static final String TAG = "Utils";
    public static void runGcIfNeeded() {
        try {
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            float memoryUsagePercent = (float) usedMemory / maxMemory * 100;

            if (memoryUsagePercent > 80) {
                Log.d(TAG, String.format("Memory usage high (%.2f%%), running GC", memoryUsagePercent));
                System.gc();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking memory usage", e);
        }
    }

}
