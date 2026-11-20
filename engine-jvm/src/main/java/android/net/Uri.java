package android.net;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** Minimal JVM Uri used by the engine's URL helpers. */
public final class Uri {
    private final String raw;
    private final URI uri;

    private Uri(String raw) {
        this.raw = raw;
        this.uri = URI.create(raw);
    }

    public static Uri parse(String value) {
        return new Uri(value);
    }

    public String getScheme() {
        return uri.getScheme();
    }

    public String getHost() {
        return uri.getHost();
    }

    public String getAuthority() {
        return uri.getAuthority();
    }

    public String getPath() {
        return uri.getPath();
    }

    public java.util.List<String> getPathSegments() {
        String path = uri.getPath();
        java.util.List<String> segments = new java.util.ArrayList<>();
        if (path != null) {
            for (String segment : path.split("/")) {
                if (!segment.isEmpty()) segments.add(segment);
            }
        }
        return segments;
    }

    public String getLastPathSegment() {
        String path = uri.getPath();
        if (path == null || path.isEmpty()) return null;
        int end = path.length();
        while (end > 0 && path.charAt(end - 1) == '/') end--;
        int start = path.lastIndexOf('/', end - 1);
        return path.substring(start + 1, end);
    }

    public String getQueryParameter(String key) {
        String query = uri.getRawQuery();
        if (query == null) return null;
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            String name = eq < 0 ? part : part.substring(0, eq);
            if (name.equals(key)) {
                String value = eq < 0 ? "" : part.substring(eq + 1);
                return URLDecoder.decode(value, StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return raw;
    }
}
