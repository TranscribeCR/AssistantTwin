package com.dhanaram.assistanttwin.ui;

import android.Manifest;
import android.app.AlarmManager;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.result.ActivityResultLauncher;

import com.dhanaram.assistanttwin.R;
import com.dhanaram.assistanttwin.service.AssistantForegroundService;

import java.util.ArrayList;
import java.util.List;

/**
 * Android forces a one-time manual tap for each of these -- no app on the
 * platform can skip that, it's an OS security boundary, not a design choice
 * here. What this screen DOES control is how much hunting that takes: "Grant
 * everything" chains straight through every dialog/settings screen in one
 * flow instead of making the user find each button themselves. Once granted,
 * nothing asks again -- AgentLoop runs full-auto from there (see agent/).
 */
public class MainActivity extends AppCompatActivity {

    private final ActivityResultLauncher<String[]> multiPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            awaitingPermissionCallback = false;
            onStepReturned();
        });

    // Each Runnable either shows a runtime-permission dialog (which returns via
    // multiPermissionLauncher's callback) or opens a Settings screen (which
    // returns via onResume when the user backs out). Either path calls
    // onStepReturned() to advance to the next one.
    private List<Runnable> grantSteps;
    private int grantIndex = -1; // -1 = not currently running the chained flow
    private boolean awaitingPermissionCallback = false; // true while a runtime-permission dialog is up,
                                                          // so onResume doesn't also advance the chain

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        grantSteps = buildGrantSteps();

        findViewById(R.id.btnGrantAll).setOnClickListener(v -> {
            grantIndex = 0;
            runCurrentStep();
        });

        findViewById(R.id.btnSms).setOnClickListener(v -> requestSms());
        findViewById(R.id.btnVoice).setOnClickListener(v -> requestVoice());
        findViewById(R.id.btnContactsCalendar).setOnClickListener(v -> requestContactsCalendar());
        findViewById(R.id.btnStorage).setOnClickListener(v -> requestStorage());
        findViewById(R.id.btnAllFiles).setOnClickListener(v -> requestAllFiles());
        findViewById(R.id.btnNotifications).setOnClickListener(v -> requestNotifications());
        findViewById(R.id.btnExactAlarm).setOnClickListener(v -> requestExactAlarm());
        findViewById(R.id.btnUsageStats).setOnClickListener(v -> openUsageAccess());
        findViewById(R.id.btnAccessibility).setOnClickListener(v -> openAccessibilitySettings());

        findViewById(R.id.btnStartService).setOnClickListener(v -> {
            ContextCompat.startForegroundService(this, new Intent(this, AssistantForegroundService.class));
            getSharedPreferences("assistant_twin", MODE_PRIVATE).edit()
                .putBoolean("service_started_before", true).apply();
            refreshStatus();
        });

        findViewById(R.id.btnOpenChat).setOnClickListener(v ->
            startActivity(new Intent(this, ChatActivity.class)));
    }

    // ---- chained "grant everything" flow ----

    private List<Runnable> buildGrantSteps() {
        List<Runnable> steps = new ArrayList<>();
        steps.add(this::requestSms);
        steps.add(this::requestVoice);
        steps.add(this::requestContactsCalendar);
        steps.add(this::requestStorage);
        steps.add(this::requestNotifications);
        steps.add(this::requestExactAlarm);
        steps.add(this::requestAllFiles);            // settings screen
        steps.add(this::openUsageAccess);            // settings screen
        steps.add(this::openAccessibilitySettings);  // settings screen, last -- the most important one
        return steps;
    }

    private void runCurrentStep() {
        if (grantIndex < 0 || grantIndex >= grantSteps.size()) {
            grantIndex = -1;
            refreshStatus();
            return;
        }
        grantSteps.get(grantIndex).run();
    }

    /** Called once a step's dialog/settings screen has returned control to us. */
    private void onStepReturned() {
        refreshStatus();
        if (grantIndex >= 0) {
            grantIndex++;
            runCurrentStep();
        }
    }

    // ---- individual permission/settings steps (reused by both the buttons and the chain) ----

    private void requestSms() {
        awaitingPermissionCallback = true;
        multiPermissionLauncher.launch(new String[]{
            Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS
        });
    }

    private void requestVoice() {
        awaitingPermissionCallback = true;
        multiPermissionLauncher.launch(new String[]{Manifest.permission.RECORD_AUDIO});
    }

    private void requestContactsCalendar() {
        awaitingPermissionCallback = true;
        multiPermissionLauncher.launch(new String[]{
            Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG
        });
    }

    private void requestStorage() {
        String[] perms = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            ? new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO}
            : new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        awaitingPermissionCallback = true;
        multiPermissionLauncher.launch(perms);
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            awaitingPermissionCallback = true;
            multiPermissionLauncher.launch(new String[]{Manifest.permission.POST_NOTIFICATIONS});
        } else {
            onStepReturned(); // nothing to request pre-13, just advance the chain
        }
    }

    private void requestExactAlarm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
        } else {
            onStepReturned();
        }
    }

    private void requestAllFiles() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + getPackageName())));
        } else {
            onStepReturned();
        }
    }

    private void openUsageAccess() {
        startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
    }

    private void openAccessibilitySettings() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // A settings-screen step returns control here (there's no direct callback
        // like the permission launcher has). A permission-dialog step returns via
        // multiPermissionLauncher's own callback instead -- skip here so the chain
        // doesn't advance twice for the same step.
        if (grantIndex >= 0 && !awaitingPermissionCallback) {
            onStepReturned();
        } else if (grantIndex < 0) {
            refreshStatus();
        }
    }

    private void refreshStatus() {
        boolean sms = hasPermission(Manifest.permission.SEND_SMS);
        boolean voice = hasPermission(Manifest.permission.RECORD_AUDIO);
        boolean contacts = hasPermission(Manifest.permission.READ_CONTACTS);
        boolean notif = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            || hasPermission(Manifest.permission.POST_NOTIFICATIONS);
        boolean exactAlarm = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
            || ((AlarmManager) getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms();
        boolean usage = hasUsageAccess();
        boolean accessibility = isAccessibilityServiceEnabled();
        boolean allFiles = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();

        TextView status = findViewById(R.id.statusText);
        status.setText(
            (grantIndex >= 0 ? "Running grant-everything flow... (" + (grantIndex + 1) + "/" + grantSteps.size() + ")\n\n" : "") +
            "SMS: " + s(sms) + "\n" +
            "Voice: " + s(voice) + "\n" +
            "Contacts/Calendar: " + s(contacts) + "\n" +
            "Notifications: " + s(notif) + "\n" +
            "Exact alarms: " + s(exactAlarm) + "\n" +
            "Usage access: " + s(usage) + "\n" +
            "Accessibility service: " + s(accessibility) + "\n" +
            "All-files access: " + s(allFiles)
        );
    }

    private String s(boolean ok) { return ok ? "granted" : "not granted"; }

    private boolean hasPermission(String perm) {
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasUsageAccess() {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();
        return !usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000, now).isEmpty();
    }

    private boolean isAccessibilityServiceEnabled() {
        String expected = getPackageName() + "/.service.AssistantAccessibilityService";
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        while (splitter.hasNext()) {
            if (splitter.next().equalsIgnoreCase(expected)) return true;
        }
        return false;
    }
}
