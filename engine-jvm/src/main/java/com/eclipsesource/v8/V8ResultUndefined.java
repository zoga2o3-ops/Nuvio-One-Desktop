package com.eclipsesource.v8;

/** J2V8 undefined-result marker. */
public class V8ResultUndefined extends RuntimeException {
    public V8ResultUndefined() {
        super("V8 result is undefined");
    }
}
