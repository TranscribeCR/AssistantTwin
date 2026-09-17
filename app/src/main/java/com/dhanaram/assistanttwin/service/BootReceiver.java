package com.dhanaram.assistanttwin.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.core.content.ContextCompat;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        // Only auto-restart if the user had already completed setup and started
        // the service at least once -- never start it fresh without consent.
        SharedPreferences prefs = context.getSharedPreferences("assistant_twin", Context.MODE_PRIVATE);
        if (prefs.getBoolean("service_started_before", false)) {
            ContextCompat.startForegroundService(context, new Intent(context, AssistantForegroundService.class));
        }
    }
}
