package com.liskovsoft.sharedutils.helpers;

import android.content.Context;

public final class MessageHelpers {
    private MessageHelpers() {
    }

    public static void showMessage(Context context, String message) {
        System.err.println(message);
    }
}
