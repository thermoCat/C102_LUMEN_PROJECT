package com.serenegiant.utils;

import android.os.Build;

/**
 * Shim for saki4510t libcommon BuildCheck. libausbc 3.2.0 only calls the few
 * methods reproduced here (isAndroid5, isLollipop, isMarshmallow); the rest of
 * the original API is intentionally omitted.
 */
public final class BuildCheck {

    public static boolean isAndroid5() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    public static boolean isLollipop() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    public static boolean isMarshmallow() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    public static boolean isAPI(final int requiredApiLevel) {
        return Build.VERSION.SDK_INT >= requiredApiLevel;
    }

    public static boolean isAndroid6() {
        return isMarshmallow();
    }

    public static boolean isAndroid7() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
    }

    public static boolean isAndroid8() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    public static boolean isAndroid9() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P;
    }

    public static boolean isAndroid10() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    private BuildCheck() {}
}
