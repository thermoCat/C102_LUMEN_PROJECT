package com.serenegiant.usb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

public final class USBMonitorCompat {
    private USBMonitorCompat() {
    }

    @SuppressWarnings("deprecation")
    public static Intent registerReceiver(
            Context context,
            BroadcastReceiver receiver,
            IntentFilter filter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        }
        return context.registerReceiver(receiver, filter);
    }
}
