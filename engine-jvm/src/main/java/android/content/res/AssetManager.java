package android.content.res;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Serves the engine's bundled JS assets from the classpath. */
public class AssetManager {
    private static final String ROOT = "/";

    public InputStream open(String fileName) throws IOException {
        InputStream stream = AssetManager.class.getResourceAsStream(ROOT + fileName);
        if (stream == null) {
            throw new FileNotFoundException(fileName);
        }
        return stream;
    }

    public String[] list(String path) throws IOException {
        java.net.URL url = AssetManager.class.getResource(ROOT + path);
        if (url == null) return new String[0];
        try {
            java.io.File file = new java.io.File(url.toURI());
            String[] names = file.list();
            return names == null ? new String[0] : names;
        } catch (java.net.URISyntaxException e) {
            return new String[0];
        }
    }
}
