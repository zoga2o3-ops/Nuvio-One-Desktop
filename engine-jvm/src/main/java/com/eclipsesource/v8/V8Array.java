package com.eclipsesource.v8;

import org.graalvm.polyglot.Value;

/** Minimal J2V8 array wrapper used by engine Java callbacks. */
public class V8Array {
    private final Object[] mValues;

    public V8Array(Value[] values) {
        mValues = new Object[values == null ? 0 : values.length];
        if (values != null) {
            for (int i = 0; i < values.length; i++) {
                mValues[i] = toJava(values[i]);
            }
        }
    }

    public int length() {
        return mValues.length;
    }

    public Object get(int index) {
        return index >= 0 && index < mValues.length ? mValues[index] : null;
    }

    public String getString(int index) {
        Object value = get(index);
        return value == null ? null : value.toString();
    }

    public int getInteger(int index) {
        Object value = get(index);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    public long getLong(int index) {
        Object value = get(index);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    public double getDouble(int index) {
        Object value = get(index);
        return value instanceof Number ? ((Number) value).doubleValue() : 0d;
    }

    public boolean getBoolean(int index) {
        Object value = get(index);
        return value instanceof Boolean && (Boolean) value;
    }

    private static Object toJava(Value value) {
        if (value == null || value.isNull()) return null;
        if (value.isString()) return value.asString();
        if (value.isBoolean()) return value.asBoolean();
        if (value.fitsInInt()) return value.asInt();
        if (value.fitsInLong()) return value.asLong();
        if (value.isNumber()) return value.asDouble();
        return value.toString();
    }
}
