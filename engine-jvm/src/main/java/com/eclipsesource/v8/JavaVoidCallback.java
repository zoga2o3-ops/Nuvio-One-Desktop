package com.eclipsesource.v8;

/**
 * J2V8-compatible callback interface.
 *
 * J2V8's void callback discards its return value. The desktop shim keeps the
 * same call shape but forwards the value back to JavaScript, because the
 * BotGuard scripts use the return value of __nativeSetInterval.
 */
public interface JavaVoidCallback {
    Object invoke(V8Object receiver, V8Array parameters);
}
