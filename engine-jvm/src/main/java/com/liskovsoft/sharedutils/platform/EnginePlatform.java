package com.liskovsoft.sharedutils.platform;

import android.content.SharedPreferences;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/** Desktop storage and app metadata used by the JVM engine. */
public final class EnginePlatform {
    public static final String PACKAGE_NAME = "com.nuvio.one.desktop";
    public static final String APP_LABEL = "Nuvio One";
    public static final String APP_VERSION = "0.1.36";

    private static volatile Path filesDir = Path.of(
            System.getProperty("user.home"), ".nuvio-one", "engine");
    private static volatile Path cacheDir = Path.of(
            System.getProperty("user.home"), ".nuvio-one", "engine-cache");
    private static final Map<String, SharedPreferences> PREFS = new ConcurrentHashMap<>();

    private EnginePlatform() {
    }

    public static void configure(Path files, Path cache) {
        filesDir = files;
        cacheDir = cache;
    }

    public static Path filesDir() {
        try {
            Files.createDirectories(filesDir);
        } catch (IOException ignored) {
        }
        return filesDir;
    }

    public static Path cacheDir() {
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException ignored) {
        }
        return cacheDir;
    }

    public static SharedPreferences preferences(String name) {
        return PREFS.computeIfAbsent(name, EnginePreferences::new);
    }

    public static String appLabel() {
        return APP_LABEL;
    }

    public static String appVersion() {
        return APP_VERSION;
    }

    private static final class EnginePreferences implements SharedPreferences {
        private final Path file;
        private final Properties values = new Properties();
        private final Object lock = new Object();
        private boolean loaded;

        private EnginePreferences(String name) {
            this.file = filesDir().resolve(name + ".properties");
        }

        private void ensureLoaded() {
            if (loaded) return;
            synchronized (lock) {
                if (loaded) return;
                if (Files.isRegularFile(file)) {
                    try (InputStream input = Files.newInputStream(file)) {
                        values.load(input);
                    } catch (IOException ignored) {
                    }
                }
                loaded = true;
            }
        }

        private void persist() {
            synchronized (lock) {
                try (OutputStream output = Files.newOutputStream(file)) {
                    values.store(output, null);
                } catch (IOException ignored) {
                }
            }
        }

        @Override
        public String getString(String key, String defValue) {
            ensureLoaded();
            return values.getProperty(key, defValue);
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            String value = getString(key, null);
            return value == null ? defValue : Boolean.parseBoolean(value);
        }

        @Override
        public int getInt(String key, int defValue) {
            String value = getString(key, null);
            try {
                return value == null ? defValue : Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return defValue;
            }
        }

        @Override
        public long getLong(String key, long defValue) {
            String value = getString(key, null);
            try {
                return value == null ? defValue : Long.parseLong(value);
            } catch (NumberFormatException e) {
                return defValue;
            }
        }

        @Override
        public boolean contains(String key) {
            ensureLoaded();
            return values.containsKey(key);
        }

        @Override
        public Map<String, ?> getAll() {
            ensureLoaded();
            Map<String, String> result = new java.util.HashMap<>();
            for (String key : values.stringPropertyNames()) {
                result.put(key, values.getProperty(key));
            }
            return result;
        }

        @Override
        public Editor edit() {
            ensureLoaded();
            return new Editor() {
                private boolean changed;

                @Override
                public Editor putString(String key, String value) {
                    if (value == null) values.remove(key);
                    else values.setProperty(key, value);
                    changed = true;
                    return this;
                }

                @Override
                public Editor putBoolean(String key, boolean value) {
                    values.setProperty(key, Boolean.toString(value));
                    changed = true;
                    return this;
                }

                @Override
                public Editor putInt(String key, int value) {
                    values.setProperty(key, Integer.toString(value));
                    changed = true;
                    return this;
                }

                @Override
                public Editor putLong(String key, long value) {
                    values.setProperty(key, Long.toString(value));
                    changed = true;
                    return this;
                }

                @Override
                public Editor remove(String key) {
                    values.remove(key);
                    changed = true;
                    return this;
                }

                @Override
                public Editor clear() {
                    values.clear();
                    changed = true;
                    return this;
                }

                @Override
                public boolean commit() {
                    if (changed) persist();
                    return true;
                }

                @Override
                public void apply() {
                    commit();
                }
            };
        }
    }
}
