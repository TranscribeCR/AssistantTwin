package com.dhanaram.assistanttwin.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * The only component that can "see into" and act inside other apps. Narrow and
 * generic on purpose: read the current screen as text, find a node by text, tap
 * it, type into it, or swipe -- the same primitives AgentActionExecutor exposes
 * to the model, so the model can drive whatever app happens to be open rather
 * than being limited to apps we've specifically coded for.
 */
public class AssistantAccessibilityService extends AccessibilityService {

    public static volatile AssistantAccessibilityService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Intentionally empty: screen state is pulled on demand (dumpScreenText)
        // rather than reacting to every event, to keep this cheap and predictable.
    }

    @Override
    public void onInterrupt() {}

    /** Flattened text snapshot of what's currently on screen. */
    public String dumpScreenText() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "";
        StringBuilder sb = new StringBuilder();
        collectText(root, sb);
        return sb.toString().trim();
    }

    private void collectText(AccessibilityNodeInfo node, StringBuilder sb) {
        if (node.getText() != null && node.getText().length() > 0) {
            sb.append(node.getText()).append('\n');
        }
        if (node.getContentDescription() != null && node.getContentDescription().length() > 0) {
            sb.append(node.getContentDescription()).append('\n');
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectText(child, sb);
                child.recycle();
            }
        }
    }

    /** First visible node whose text or content-description contains [query]. */
    public AccessibilityNodeInfo findNodeByText(String query) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        return searchNode(root, query);
    }

    private AccessibilityNodeInfo searchNode(AccessibilityNodeInfo node, String query) {
        CharSequence text = node.getText() != null ? node.getText() : node.getContentDescription();
        if (text != null && text.toString().toLowerCase().contains(query.toLowerCase())) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            AccessibilityNodeInfo found = searchNode(child, query);
            if (found != null) return found;
            child.recycle();
        }
        return null;
    }

    public boolean tapNode(AccessibilityNodeInfo node) {
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        return tapAt(rect.centerX(), rect.centerY());
    }

    public boolean tapAt(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, 50);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        return dispatchGesture(gesture, null, null);
    }

    public boolean swipe(float x1, float y1, float x2, float y2, long durationMs) {
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, durationMs);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        return dispatchGesture(gesture, null, null);
    }

    /** Sets text on an editable node without opening the keyboard. */
    public boolean setTextOnNode(AccessibilityNodeInfo node, String text) {
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    public boolean clickNode(AccessibilityNodeInfo node) {
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    public void goHome() { performGlobalAction(GLOBAL_ACTION_HOME); }
    public void goBack() { performGlobalAction(GLOBAL_ACTION_BACK); }
    public void openRecents() { performGlobalAction(GLOBAL_ACTION_RECENTS); }
}
