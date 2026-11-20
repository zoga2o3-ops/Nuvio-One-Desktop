package com.liskovsoft.googlecommon.common.js;

import com.liskovsoft.sharedutils.mylogger.Log;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.util.List;

/** JVM JavaScript runtime used by the engine's nsig/PoToken scripts. */
public final class V8Runtime {
    private static final String TAG = V8Runtime.class.getSimpleName();
    private static V8Runtime sInstance;

    private V8Runtime() {
    }

    public static V8Runtime instance() {
        if (sInstance == null) sInstance = new V8Runtime();
        return sInstance;
    }

    public static void unhold() {
        sInstance = null;
    }

    public String evaluate(final String source) {
        try {
            return evaluateSafe(source);
        } catch (RuntimeException e) {
            Log.e(TAG, e.getMessage());
            return null;
        }
    }

    public String evaluateWithErrors(final String source) {
        return evaluateSafe(source);
    }

    public String evaluate(final List<String> sources) {
        try {
            return evaluateSafe(sources);
        } catch (RuntimeException e) {
            Log.e(TAG, e.getMessage());
            return null;
        }
    }

    public String evaluateWithErrors(final List<String> sources) {
        return evaluateSafe(sources);
    }

    private String evaluateSafe(final String source) {
        try (Context context = newContext()) {
            Value value = context.eval(source(source));
            return value == null || value.isNull() ? null : value.toString();
        }
    }

    private String evaluateSafe(final List<String> sources) {
        String result = null;
        try (Context context = newContext()) {
            for (String source : sources) {
                try {
                    Value value = context.eval(source(source));
                    if (value != null && !value.isNull()) result = value.toString();
                } catch (RuntimeException ignored) {
                }
            }
        }
        return result;
    }

    private Source source(String source) {
        try {
            return Source.newBuilder("js", source, "engine.js").build();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Context newContext() {
        return Context.newBuilder("js")
                .allowAllAccess(false)
                .option("engine.WarnInterpreterOnly", "false")
                .build();
    }
}
