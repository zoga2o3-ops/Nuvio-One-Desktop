package com.eclipsesource.v8;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

/**
 * J2V8-compatible façade backed by GraalJS.
 *
 * The SmartTube engine only uses a small V8 surface: eval a script, register a Java
 * callback and keep one global object alive between calls.
 */
public class V8 {
    private final Object mLock = new Object();
    private final Locker mLocker = new Locker();
    private Context mContext;

    private V8() {
    }

    public static V8 createV8Runtime() {
        return new V8();
    }

    public Locker getLocker() {
        return mLocker;
    }

    public String executeStringScript(String script) {
        synchronized (mLock) {
            try {
                Value result = context().eval("js", script);
                return stringify(result);
            } catch (PolyglotException e) {
                throw new V8ScriptExecutionException(e.getMessage());
            }
        }
    }

    public void executeVoidScript(String script) {
        synchronized (mLock) {
            try {
                context().eval("js", script);
            } catch (PolyglotException e) {
                throw new V8ScriptExecutionException(e.getMessage());
            }
        }
    }

    public Object executeJSFunction(String functionName, Object... args) {
        synchronized (mLock) {
            try {
                Value function = context().getBindings("js").getMember(functionName);
                if (function == null || !function.canExecute()) {
                    return null;
                }
                return stringify(function.execute(args));
            } catch (PolyglotException e) {
                throw new V8ScriptExecutionException(e.getMessage());
            }
        }
    }

    public void registerJavaMethod(final JavaVoidCallback callback, final String jsFunctionName) {
        synchronized (mLock) {
            context().getBindings("js").putMember(jsFunctionName, (ProxyExecutable) arguments ->
                    callback.invoke(new V8Object(), new V8Array(arguments))
            );
        }
    }

    public void release(boolean reportMemoryUsage) {
        synchronized (mLock) {
            if (mContext != null) {
                try {
                    mContext.close(true);
                } catch (RuntimeException ignored) {
                }
                mContext = null;
            }
        }
    }

    private Context context() {
        if (mContext == null) {
            Context context = Context.newBuilder("js")
                    .allowCreateThread(true)
                    .allowExperimentalOptions(true)
                    .option("engine.WarnInterpreterOnly", "false")
                    .option("js.console", "true")
                    .build();
            // The ported BotGuard scripts expect a browser-like global.
            context.eval("js", "globalThis.window = globalThis; globalThis.self = globalThis;");
            mContext = context;
        }
        return mContext;
    }

    private static String stringify(Value value) {
        if (value == null || value.isNull()) return null;
        if (value.isString()) return value.asString();
        if (value.isBoolean()) return Boolean.toString(value.asBoolean());
        if (value.isNumber()) {
            if (value.fitsInInt()) return Integer.toString(value.asInt());
            if (value.fitsInLong()) return Long.toString(value.asLong());
            return Double.toString(value.asDouble());
        }
        String text = value.toString();
        return "undefined".equals(text) ? null : text;
    }
}
