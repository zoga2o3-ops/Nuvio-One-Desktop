package com.liskovsoft.sharedutils.platform;

import android.content.Context;

/** Desktop Context used to initialize the engine. */
public final class EngineContext extends Context {
    private static final EngineContext INSTANCE = new EngineContext();

    private EngineContext() {
    }

    public static EngineContext get() {
        return INSTANCE;
    }
}
