package com.serenegiant.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;

/**
 * Shim for saki4510t libcommon PermissionCheck. libausbc 3.2.0 only calls the
 * methods reproduced here.
 */
public final class PermissionCheck {

    public static boolean hasPermission(final Context context, final String permission) {
        if (context == null || permission == null) return false;
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasCamera(final Context context) {
        return hasPermission(context, Manifest.permission.CAMERA);
    }

    public static boolean hasAudio(final Context context) {
        return hasPermission(context, Manifest.permission.RECORD_AUDIO);
    }

    public static boolean hasNetwork(final Context context) {
        return hasPermission(context, Manifest.permission.INTERNET);
    }

    public static boolean hasWriteExternalStorage(final Context context) {
        return hasPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    public static boolean hasReadExternalStorage(final Context context) {
        return hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    private PermissionCheck() {}
}
