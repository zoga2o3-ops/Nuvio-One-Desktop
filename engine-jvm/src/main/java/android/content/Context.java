package android.content;

import com.liskovsoft.sharedutils.platform.EnginePlatform;

import java.io.File;

/** Minimal JVM stand-in for the parts of android.content.Context the engine uses. */
public class Context {
    public static final int MODE_PRIVATE = 0;
    private final android.content.res.AssetManager mAssets = new android.content.res.AssetManager();

    public File getFilesDir() {
        return EnginePlatform.filesDir().toFile();
    }

    public File getCacheDir() {
        return EnginePlatform.cacheDir().toFile();
    }

    public android.content.res.AssetManager getAssets() {
        return mAssets;
    }

    public android.content.res.Resources getResources() {
        return android.content.res.Resources.getSystem();
    }

    public String getPackageName() {
        return EnginePlatform.PACKAGE_NAME;
    }

    public Context getApplicationContext() {
        return this;
    }

    public SharedPreferences getSharedPreferences(String name, int mode) {
        return EnginePlatform.preferences(name);
    }
}
