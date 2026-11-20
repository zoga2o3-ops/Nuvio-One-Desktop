package androidx.core.text;

/** JVM no-op stand-in for BidiFormatter. */
public final class BidiFormatter {
    private static final BidiFormatter INSTANCE = new BidiFormatter();

    private BidiFormatter() {
    }

    public static BidiFormatter getInstance() {
        return INSTANCE;
    }

    public CharSequence unicodeWrap(CharSequence value) {
        return value;
    }
}
