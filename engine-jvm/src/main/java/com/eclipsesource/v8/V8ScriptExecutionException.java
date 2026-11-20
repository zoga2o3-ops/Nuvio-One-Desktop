package com.eclipsesource.v8;

/** J2V8 execution error stand-in. */
public class V8ScriptExecutionException extends RuntimeException {
    public V8ScriptExecutionException(String message) {
        super(message);
    }

    public V8ScriptExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
