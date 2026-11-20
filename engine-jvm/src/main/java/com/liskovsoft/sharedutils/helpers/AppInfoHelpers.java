package com.liskovsoft.sharedutils.helpers;

import android.content.Context;

import com.liskovsoft.sharedutils.platform.EnginePlatform;

public final class AppInfoHelpers {
    private AppInfoHelpers() {
    }

    public static String getAppVersionName(Context context) {
        return EnginePlatform.appVersion();
    }

    public static String getAppLabel(Context context) {
        return EnginePlatform.appLabel();
    }
}
