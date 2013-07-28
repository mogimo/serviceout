package jp.gr.java_conf.mogimo.oos;

import android.util.Log;

public class Debug {
    private static final String TAG = "Mogimo";
    public static final boolean DEBUG = true;

    public static void log(String msg) {
        if (DEBUG) Log.d(TAG, msg);
    }
}
