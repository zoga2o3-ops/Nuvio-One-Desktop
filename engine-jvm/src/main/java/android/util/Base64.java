package android.util;

import java.nio.charset.StandardCharsets;

/** JVM stand-in for android.util.Base64. */
public final class Base64 {
    public static final int DEFAULT = 0;
    public static final int NO_PADDING = 1;
    public static final int NO_WRAP = 2;
    public static final int URL_SAFE = 8;

    private Base64() {
    }

    public static byte[] decode(String str, int flags) {
        if (str == null) return new byte[0];
        String value = str.replace("\n", "").replace("\r", "");
        try {
            if ((flags & URL_SAFE) != 0) {
                return java.util.Base64.getUrlDecoder().decode(value);
            }
            return java.util.Base64.getMimeDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            return java.util.Base64.getUrlDecoder().decode(value);
        }
    }

    public static byte[] decode(byte[] input, int flags) {
        return decode(new String(input, StandardCharsets.ISO_8859_1), flags);
    }

    public static String encodeToString(byte[] input, int flags) {
        java.util.Base64.Encoder encoder = (flags & URL_SAFE) != 0
                ? java.util.Base64.getUrlEncoder()
                : java.util.Base64.getEncoder();
        if ((flags & NO_PADDING) != 0) {
            encoder = encoder.withoutPadding();
        }
        return encoder.encodeToString(input == null ? new byte[0] : input);
    }

    public static String encode(byte[] input, int flags) {
        return encodeToString(input, flags);
    }
}
