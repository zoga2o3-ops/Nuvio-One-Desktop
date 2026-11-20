package com.liskovsoft.sharedutils.locale;

import android.content.Context;

import java.util.Locale;

public final class LocaleUtility {
    private LocaleUtility() {
    }

    public static Locale getCurrentLocale(Context context) {
        return Locale.getDefault();
    }
}
