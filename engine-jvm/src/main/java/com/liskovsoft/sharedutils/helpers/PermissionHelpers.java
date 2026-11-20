package com.liskovsoft.sharedutils.helpers;

import android.content.Context;

public final class PermissionHelpers {
    private PermissionHelpers() {
    }

    public static boolean hasStoragePermissions(Context context) {
        return true;
    }
}
