package com.dhanaram.assistanttwin.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dhanaram.assistanttwin.R;
import com.dhanaram.assistanttwin.agent.AgentLoop;
import com.dhanaram.assistanttwin.llm.LlamaEngine;
import com.dhanaram.assistanttwin.voice.VoiceInputManager;
import com.dhanaram.assistanttwin.voice.VoiceOutputManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The twin's front door: type or speak a goal, watch it work step by step (open
 * an app, read the screen, tap/type, repeat) with live token streaming, exactly
 * like a computer-use agent rather than a single fixed command.
 */
public class ChatActivity extends AppCompatActivity {

    private final List<ChatMessage> messages = new ArrayList<>();
    private ChatAdapter adapter;
    private RecyclerView recyclerView;
    private VoiceInputManager voiceIn;
    private VoiceOutputManager voiceOut;
    private LlamaEngine engine;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        setTitle("Assistant Twin");

        recyclerView = findViewById(R.id.chatRecyclerView);
        adapter = new ChatAdapter(messages);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        voiceIn = new VoiceInputManager(this);
        voiceOut = new VoiceOutputManager(this);
        engine = new LlamaEngine();

        EditText input = findViewById(R.id.messageInput);

        addMessage("assistant", "Loading the on-device model…");
        executor.execute(() -> {
            File modelFile = new File(getFilesDir(), "models/qwen2.5-0.5b-instruct-q4_k_m.gguf");
            boolean ok = modelFile.exists() &&
                engine.loadModel(modelFile.getAbsolutePath(), 2048, Runtime.getRuntime().availableProcessors());
            runOnUiThread(() -> adapter.updateLast(ok
                ? "Ready. Tell me what to do, or tap the mic."
                : "Model not found at " + modelFile.getAbsolutePath() + " -- see README to get a GGUF model onto the device."));
        });

        findViewById(R.id.sendButton).setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (!text.isEmpty()) {
                input.setText("");
                runGoal(text);
            }
        });

        findViewById(R.id.micButton).setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Grant microphone access from the main screen first", Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, "Listening…", Toast.LENGTH_SHORT).show();
            voiceIn.startListening(this::runGoal,
                err -> Toast.makeText(this, err, Toast.LENGTH_SHORT).show(),
                partial -> {});
        });
    }

    private void runGoal(String goal) {
        addMessage("user", goal);
        addMessage("assistant", "…");

        executor.execute(() -> {
            if (!engine.isLoaded()) {
                runOnUiThread(() -> adapter.updateLast("Model isn't loaded yet -- see README."));
                return;
            }

            StringBuilder streamed = new StringBuilder();
            AgentLoop.run(getApplicationContext(), engine, goal, new AgentLoop.StepListener() {
                @Override public void onStep(String modelThought, String actionJson, String observation) {
                    streamed.setLength(0);
                    runOnUiThread(() -> adapter.updateLast("→ " + observation));
                }
                @Override public void onFinished(String summary) {
                    runOnUiThread(() -> {
                        adapter.updateLast(summary);
                        voiceOut.speak(summary);
                    });
                }
                @Override public void onToken(String token) {
                    streamed.append(token);
                    runOnUiThread(() -> adapter.updateLast(streamed.toString()));
                }
            });
        });
    }

    private void addMessage(String role, String content) {
        runOnUiThread(() -> {
            adapter.addMessage(new ChatMessage(role, content));
            recyclerView.scrollToPosition(messages.size() - 1);
        });
    }

    @Override
    protected void onDestroy() {
        voiceIn.destroy();
        voiceOut.shutdown();
        executor.execute(() -> engine.unload());
        executor.shutdown();
        super.onDestroy();
    }
}
