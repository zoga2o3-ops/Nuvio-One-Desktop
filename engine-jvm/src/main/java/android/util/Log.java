package android.util;

/** JVM stand-in for android.util.Log. */
public final class Log {
    private Log() {
    }

    public static int d(String tag, String msg) {
        com.liskovsoft.sharedutils.mylogger.Log.d(tag, msg);
        return 0;
    }

    public static int i(String tag, String msg) {
        com.liskovsoft.sharedutils.mylogger.Log.d(tag, msg);
        return 0;
    }

    public static int w(String tag, String msg) {
        com.liskovsoft.sharedutils.mylogger.Log.e(tag, msg);
        return 0;
    }

    public static int e(String tag, String msg) {
        com.liskovsoft.sharedutils.mylogger.Log.e(tag, msg);
        return 0;
    }

    public static int e(String tag, String msg, Throwable tr) {
        com.liskovsoft.sharedutils.mylogger.Log.e(tag, msg + " " + tr);
        return 0;
    }
}
