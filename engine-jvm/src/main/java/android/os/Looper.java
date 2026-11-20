package android.os;

import java.awt.EventQueue;

/** JVM stand-in for android.os.Looper. Compose Desktop runs on the AWT event thread. */
public final class Looper {
    private static final Looper MAIN = new Looper();

    private Looper() {
    }

    public static Looper getMainLooper() {
        return MAIN;
    }

    public static Looper myLooper() {
        return EventQueue.isDispatchThread() ? MAIN : null;
    }
}
