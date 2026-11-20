package com.liskovsoft.sharedutils.okhttp;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;

import java.util.concurrent.TimeUnit;

/** JVM OkHttp defaults shared by the engine. */
final class OkHttpCommons {
    public static final long CONNECT_TIMEOUT_MS = 20_000;
    public static final long READ_TIMEOUT_MS = 20_000;
    public static final long WRITE_TIMEOUT_MS = 20_000;
    public static boolean enableProfiler = false;

    private OkHttpCommons() {
    }

    static OkHttpClient.Builder setupBuilder(OkHttpClient.Builder builder) {
        builder.connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        builder.readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        builder.writeTimeout(WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        builder.connectionPool(new ConnectionPool(20, 5, TimeUnit.MINUTES));
        builder.retryOnConnectionFailure(true);
        return builder;
    }
}
