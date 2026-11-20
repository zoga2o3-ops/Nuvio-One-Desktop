package com.eclipsesource.v8;

import java.util.concurrent.locks.ReentrantLock;

/** J2V8 locker stand-in. */
public class Locker {
    private final ReentrantLock mLock = new ReentrantLock();

    public Locker() {
    }

    public void acquire() {
        mLock.lock();
    }

    public void release() {
        if (mLock.isHeldByCurrentThread()) {
            mLock.unlock();
        }
    }
}
