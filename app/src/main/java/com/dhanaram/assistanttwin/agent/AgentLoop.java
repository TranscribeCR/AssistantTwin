package com.dhanaram.assistanttwin.agent;

import android.content.Context;
import org.json.JSONObject;

import com.dhanaram.assistanttwin.actions.AppInventory;
import com.dhanaram.assistanttwin.llm.LlamaEngine;

import java.util.List;

/**
 * The "act like codex" piece: instead of one instruction -> one action, this runs
 * an observe -> decide -> act -> observe loop, same shape as a computer-use agent.
 * The model is not told to only pick from a fixed list of apps -- it's given the
 * generic primitives (open_app, tap_text, type_text, swipe, key, read_screen, ...)
 * and can drive literally whatever is on screen, in any app, across as many steps
 * as it needs, until it emits {"action":"done"} or hits maxSteps.
 */
public class AgentLoop {

    public interface StepListener {
        /** Called after every step with what the model decided and what happened. */
        void onStep(String modelThought, String actionJson, String observation);
        void onFinished(String summary);
        void onToken(String token); // live streaming, forwarded from LlamaEngine
    }

    // Codex-style full-auto: run to completion without pausing to ask whether to
    // continue. This cap exists only to stop a genuinely stuck/looping model from
    // running forever, not to check in with the user -- if it's hit, the loop just
    // reports what it got done rather than requesting permission to keep going.
    private static final int MAX_STEPS = 40;

    public static void run(Context context, LlamaEngine engine, String userGoal, StepListener listener) {
        StringBuilder transcript = new StringBuilder();
        transcript.append(buildSystemPrompt(context)).append("\n");
        transcript.append("User goal: ").append(userGoal).append("\n");

        for (int step = 0; step < MAX_STEPS; step++) {
            final StringBuilder tokenBuffer = new StringBuilder();
            String modelOutput = engine.generate(
                transcript.toString() + "\nNext action (one JSON object only):",
                256,
                token -> {
                    tokenBuffer.append(token);
                    listener.onToken(token);
                }
            );
            if (modelOutput == null || modelOutput.isEmpty()) modelOutput = tokenBuffer.toString();

            JSONObject action = extractJson(modelOutput);
            if (action == null) {
                // Model replied in plain text instead of an action -- treat it as
                // the final answer rather than looping forever.
                listener.onFinished(modelOutput.trim());
                return;
            }

            String actionType = action.optString("action", "");
            String observation = AgentActionExecutor.execute(context, action);
            listener.onStep(modelOutput, action.toString(), observation);

            if ("done".equals(actionType) || "reply".equals(actionType)) {
                listener.onFinished(observation);
                return;
            }

            transcript.append("Action: ").append(action).append("\n");
            transcript.append("Observation: ").append(truncate(observation, 1500)).append("\n");
        }

        listener.onFinished("Reached " + MAX_STEPS + " actions on this goal -- here's where things stand: " +
            "check the last few steps above for what's been done so far.");
    }

    private static String buildSystemPrompt(Context context) {
        List<AppInventory.AppEntry> apps = AppInventory.listInstalledApps(context);
        StringBuilder appList = new StringBuilder();
        for (AppInventory.AppEntry app : apps) {
            appList.append(app.label).append(" (").append(app.packageName).append("), ");
        }

        return "You are Assistant Twin, an agent that controls this Android phone directly. " +
            "You are NOT limited to a fixed list of apps or actions -- you can open, read, and " +
            "operate ANY app currently installed by reading its screen and tapping/typing on it, " +
            "the same way a person would. Known installed apps (not exhaustive of what you can do, " +
            "just for reference): " + appList + "\n" +
            "On every turn reply with EXACTLY one JSON object, nothing else, one of:\n" +
            "{\"action\":\"open_app\",\"package\":\"...\"}\n" +
            "{\"action\":\"read_screen\"}\n" +
            "{\"action\":\"tap_text\",\"query\":\"...\"}\n" +
            "{\"action\":\"tap_xy\",\"x\":123,\"y\":456}\n" +
            "{\"action\":\"type_text\",\"query\":\"...\",\"text\":\"...\"}\n" +
            "{\"action\":\"swipe\",\"x1\":0,\"y1\":0,\"x2\":0,\"y2\":0}\n" +
            "{\"action\":\"key\",\"key\":\"back|home|recents\"}\n" +
            "{\"action\":\"send_sms\",\"to\":\"...\",\"message\":\"...\"}\n" +
            "{\"action\":\"set_alarm\",\"hour\":7,\"minute\":30,\"label\":\"...\"}\n" +
            "{\"action\":\"wait\",\"ms\":1000}\n" +
            "{\"action\":\"done\",\"summary\":\"...\"} once the goal is complete\n" +
            "Read the screen after opening an app before assuming what's on it.";
    }

    private static JSONObject extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start == -1 || end == -1 || end < start) return null;
        try {
            return new JSONObject(text.substring(start, end + 1));
        } catch (Exception e) {
            return null;
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
