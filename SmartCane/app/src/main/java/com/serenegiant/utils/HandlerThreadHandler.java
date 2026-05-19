package com.serenegiant.utils;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

/**
 * Shim for the saki4510t libcommon HandlerThreadHandler that libausbc 3.2.0
 * (com.github.jiangdongguo.AndroidUSBCamera:libuvc) calls into. The original
 * lived on jcenter (com.serenegiant:common) which is dead, so we reproduce the
 * tiny API surface here. Backs the Handler with a private HandlerThread.
 */
public final class HandlerThreadHandler extends Handler {

    public static HandlerThreadHandler createHandler() {
        return createHandler("HandlerThreadHandler");
    }

    public static HandlerThreadHandler createHandler(final String name) {
        final HandlerThread thread = new HandlerThread(name);
        thread.start();
        return new HandlerThreadHandler(thread.getLooper());
    }

    public static HandlerThreadHandler createHandler(final String name, final Callback callback) {
        final HandlerThread thread = new HandlerThread(name);
        thread.start();
        return new HandlerThreadHandler(thread.getLooper(), callback);
    }

    private HandlerThreadHandler(final Looper looper) {
        super(looper);
    }

    private HandlerThreadHandler(final Looper looper, final Callback callback) {
        super(looper, callback);
    }
}
