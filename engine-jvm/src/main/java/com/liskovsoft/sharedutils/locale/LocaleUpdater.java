package com.liskovsoft.sharedutils.locale;

import android.content.Context;

import java.util.Locale;

public final class LocaleUpdater {
    private LocaleUpdater() {
    }

    public static Locale getSavedLocale(Context context) {
        return Locale.getDefault();
    }
}
