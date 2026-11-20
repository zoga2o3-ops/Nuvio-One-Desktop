package com.liskovsoft.sharedutils.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import com.liskovsoft.sharedutils.helpers.FileHelpers;

import java.io.File;

public class SharedPreferencesBase {
    private final SharedPreferences mPrefs;
    private Context mContext;

    public SharedPreferencesBase(Context context, String prefName) {
        this(context, prefName, -1, false);
    }

    public SharedPreferencesBase(Context context, String prefName, boolean limitMaxSize) {
        this(context, prefName, -1, limitMaxSize);
    }

    public SharedPreferencesBase(Context context, String prefName, int defValResId) {
        this(context, prefName, defValResId, false);
    }

    public SharedPreferencesBase(Context context) {
        this(context, null, -1, false);
    }

    public SharedPreferencesBase(Context context, int defValResId) {
        this(context, null, defValResId, false);
    }

    public SharedPreferencesBase(Context context, String prefName, int defValResId, boolean limitMaxSize) {
        setContext(context);
        if (prefName != null) {
            mPrefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE);
        } else {
            prefName = context.getPackageName() + "_preferences";
            mPrefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE);
        }
    }

    public Context getContext() {
        return mContext;
    }

    private void setContext(Context context) {
        if (context != null) {
            mContext = context.getApplicationContext();
        }
    }

    public void putLong(String key, long val) {
        mPrefs.edit().putLong(key, val).apply();
    }

    public long getLong(String key, long defVal) {
        return mPrefs.getLong(key, defVal);
    }

    public void putInt(String key, int val) {
        mPrefs.edit().putInt(key, val).apply();
    }

    public int getInt(String key, int defVal) {
        return mPrefs.getInt(key, defVal);
    }

    public void putBoolean(String key, boolean val) {
        mPrefs.edit().putBoolean(key, val).apply();
    }

    public boolean getBoolean(String key, boolean defVal) {
        return mPrefs.getBoolean(key, defVal);
    }

    public void putString(String key, String val) {
        mPrefs.edit().putString(key, val).apply();
    }

    public String getString(String key, String defVal) {
        return mPrefs.getString(key, defVal);
    }

    public void clear() {
        mPrefs.edit().clear().apply();
    }

    protected String getPrefsDir() {
        return null;
    }

    public final String getData(String key) {
        File source = getStorageFile(key);
        String data = FileHelpers.getFileContents(source);
        if (data == null) {
            data = getString(key, null);
            putString(key, null);
            setData(key, data);
        }
        return data;
    }

    public final void setData(String key, String data) {
        File destination = getStorageFile(key);
        FileHelpers.stringToFile(data, destination);
    }

    private File getStorageFile(String key) {
        String prefsDir = getPrefsDir();
        if (prefsDir == null) {
            throw new IllegalStateException("getPrefsDir() cannot be null");
        }
        if (key == null) return null;
        return new File(FileHelpers.getFilesDir(getContext()), prefsDir + "/" + key);
    }
}
