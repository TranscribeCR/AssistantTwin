package com.dhanaram.assistanttwin.actions;

import android.content.Context;
import android.content.Intent;
import android.provider.AlarmClock;

import java.util.ArrayList;
import java.util.Set;

public class AlarmHelper {

    /**
     * Sets an alarm via the system Clock app's implicit intent -- needs no dangerous
     * permission at all, and lets the user's actual clock app (Samsung/Xiaomi/stock)
     * own the alarm rather than a deprecated broadcast.
     */
    public static void setAlarm(Context context, int hour, int minute, String label) {
        setAlarm(context, hour, minute, label, null);
    }

    public static void setAlarm(Context context, int hour, int minute, String label, Set<Integer> days) {
        Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
        intent.putExtra(AlarmClock.EXTRA_HOUR, hour);
        intent.putExtra(AlarmClock.EXTRA_MINUTES, minute);
        intent.putExtra(AlarmClock.EXTRA_MESSAGE, label);
        intent.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
        if (days != null && !days.isEmpty()) {
            intent.putExtra(AlarmClock.EXTRA_DAYS, new ArrayList<>(days));
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void setTimer(Context context, int seconds, String label) {
        Intent intent = new Intent(AlarmClock.ACTION_SET_TIMER);
        intent.putExtra(AlarmClock.EXTRA_LENGTH, seconds);
        intent.putExtra(AlarmClock.EXTRA_MESSAGE, label);
        intent.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
