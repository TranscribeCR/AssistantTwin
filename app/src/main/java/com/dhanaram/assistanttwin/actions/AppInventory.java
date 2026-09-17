package com.dhanaram.assistanttwin.actions;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AppInventory {

    public static class AppEntry {
        public final String label;
        public final String packageName;
        public AppEntry(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
        }
    }

    /** All launchable apps on the device -- needs no permission. */
    public static List<AppEntry> listInstalledApps(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        Map<String, String> byPackage = new LinkedHashMap<>();
        for (ResolveInfo info : pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)) {
            byPackage.put(info.activityInfo.packageName, info.loadLabel(pm).toString());
        }

        List<AppEntry> result = new ArrayList<>();
        for (Map.Entry<String, String> e : byPackage.entrySet()) {
            result.add(new AppEntry(e.getValue(), e.getKey()));
        }
        result.sort(Comparator.comparing(a -> a.label));
        return result;
    }

    /** Most recently foregrounded package -- needs PACKAGE_USAGE_STATS (granted in Settings). */
    public static String currentForegroundApp(Context context) {
        UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();
        UsageEvents events = usm.queryEvents(now - 10_000, now);
        String lastPackage = null;
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastPackage = event.getPackageName();
            }
        }
        return lastPackage;
    }

    public static boolean launchApp(Context context, String packageName) {
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent == null) return false;
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(launchIntent);
        return true;
    }
}
