package com.dhanaram.assistanttwin.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

import com.dhanaram.assistanttwin.agent.AgentLoop;
import com.dhanaram.assistanttwin.llm.LlamaEngine;

import java.io.File;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Keeps the on-device model loaded and runs one AgentLoop per submitted instruction,
 * even while the app is backgrounded. Everything here runs locally -- no server.
 */
public class AssistantForegroundService extends Service {

    private static final String CHANNEL_ID = "assistant_twin_status";
    private static final int NOTIF_ID = 1001;
    private static final BlockingQueue<String> pendingRequests = new LinkedBlockingQueue<>();

    public static void submit(String text) {
        pendingRequests.offer(text);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LlamaEngine engine;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIF_ID, buildNotification("Loading model…"));

        engine = new LlamaEngine();
        executor.execute(() -> {
            File modelFile = new File(getFilesDir(), "models/qwen2.5-0.5b-instruct-q4_k_m.gguf");
            if (!modelFile.exists()) {
                updateNotification("Model not found at " + modelFile.getAbsolutePath());
                return;
            }
            boolean ok = engine.loadModel(modelFile.getAbsolutePath(), 2048, Runtime.getRuntime().availableProcessors());
            updateNotification(ok ? "Assistant Twin ready" : "Model failed to load -- check logcat");
            if (ok) listenForCommands();
        });
    }

    private void listenForCommands() {
        executor.execute(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String goal = pendingRequests.take();
                    updateNotification("Working on: \"" + goal + "\"");
                    AgentLoop.run(getApplicationContext(), engine, goal, new AgentLoop.StepListener() {
                        @Override public void onStep(String modelThought, String actionJson, String observation) {
                            updateNotification(observation.isEmpty() ? "…" : observation);
                        }
                        @Override public void onFinished(String summary) {
                            updateNotification(summary.isEmpty() ? "Done" : summary);
                        }
                        @Override public void onToken(String token) { /* no live UI while backgrounded */ }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("command")) {
            submit(intent.getStringExtra("command"));
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        if (engine != null) engine.unload();
        super.onDestroy();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Assistant Twin status", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Assistant Twin")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIF_ID, buildNotification(text));
    }
}
