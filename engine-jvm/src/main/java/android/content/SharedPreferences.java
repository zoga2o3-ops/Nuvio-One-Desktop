package android.content;

import java.util.Map;

/** Minimal JVM stand-in for the Android SharedPreferences API used by the engine. */
public interface SharedPreferences {
    String getString(String key, String defValue);

    boolean getBoolean(String key, boolean defValue);

    int getInt(String key, int defValue);

    long getLong(String key, long defValue);

    boolean contains(String key);

    Map<String, ?> getAll();

    Editor edit();

    interface Editor {
        Editor putString(String key, String value);

        Editor putBoolean(String key, boolean value);

        Editor putInt(String key, int value);

        Editor putLong(String key, long value);

        Editor remove(String key);

        Editor clear();

        boolean commit();

        void apply();
    }
}
