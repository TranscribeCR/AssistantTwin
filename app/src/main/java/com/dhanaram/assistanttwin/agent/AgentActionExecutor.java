package com.dhanaram.assistanttwin.agent;

import android.content.Context;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONException;
import org.json.JSONObject;

import com.dhanaram.assistanttwin.actions.AlarmHelper;
import com.dhanaram.assistanttwin.actions.AppInventory;
import com.dhanaram.assistanttwin.actions.MessagingHelper;
import com.dhanaram.assistanttwin.service.AssistantAccessibilityService;

/**
 * Generic device-control primitives, deliberately NOT a fixed per-app action list.
 * The model can drive whatever app is currently open -- WhatsApp, banking apps,
 * a browser, a game -- the same way a person would: by reading what's on screen
 * and tapping/typing on it. open_app just switches which app is in front; every
 * other primitive then acts on whatever that app renders. This is what makes it
 * general-purpose rather than limited to apps we've hardcoded support for.
 *
 * One JSON action in, one plain-text result out. AgentLoop calls execute() once
 * per step and feeds the result back to the model as its next observation.
 */
public class AgentActionExecutor {

    public static String execute(Context context, JSONObject action) {
        String type = action.optString("action", "");
        try {
            switch (type) {
                case "open_app": {
                    String pkg = action.getString("package");
                    boolean ok = AppInventory.launchApp(context, pkg);
                    return ok ? "Opened " + pkg : "Could not find app: " + pkg;
                }
                case "tap_text": {
                    AssistantAccessibilityService svc = requireAccessibility();
                    String query = action.getString("query");
                    AccessibilityNodeInfo node = svc.findNodeByText(query);
                    if (node == null) return "Not found on screen: \"" + query + "\"";
                    boolean ok = svc.clickNode(node);
                    return ok ? "Tapped \"" + query + "\"" : "Found \"" + query + "\" but the tap failed";
                }
                case "tap_xy": {
                    AssistantAccessibilityService svc = requireAccessibility();
                    float x = (float) action.getDouble("x");
                    float y = (float) action.getDouble("y");
                    boolean ok = svc.tapAt(x, y);
                    return ok ? "Tapped at (" + x + "," + y + ")" : "Tap gesture failed";
                }
                case "type_text": {
                    AssistantAccessibilityService svc = requireAccessibility();
                    String query = action.getString("query");
                    String text = action.getString("text");
                    AccessibilityNodeInfo node = svc.findNodeByText(query);
                    if (node == null) return "Field not found: \"" + query + "\"";
                    boolean ok = svc.setTextOnNode(node, text);
                    return ok ? "Typed into \"" + query + "\"" : "Found field but typing failed";
                }
                case "swipe": {
                    AssistantAccessibilityService svc = requireAccessibility();
                    float x1 = (float) action.getDouble("x1");
                    float y1 = (float) action.getDouble("y1");
                    float x2 = (float) action.getDouble("x2");
                    float y2 = (float) action.getDouble("y2");
                    boolean ok = svc.swipe(x1, y1, x2, y2, 300);
                    return ok ? "Swiped" : "Swipe failed";
                }
                case "key": {
                    AssistantAccessibilityService svc = requireAccessibility();
                    String key = action.getString("key");
                    switch (key) {
                        case "back": svc.goBack(); return "Pressed back";
                        case "home": svc.goHome(); return "Pressed home";
                        case "recents": svc.openRecents(); return "Opened recents";
                        default: return "Unknown key: " + key;
                    }
                }
                case "read_screen": {
                    AssistantAccessibilityService svc = requireAccessibility();
                    String text = svc.dumpScreenText();
                    return text.isEmpty() ? "(screen has no readable text)" : text;
                }
                case "send_sms": {
                    String to = action.getString("to");
                    String message = action.getString("message");
                    boolean ok = MessagingHelper.sendSms(context, to, message);
                    return ok ? "Sent SMS to " + to : "SMS permission not granted";
                }
                case "set_alarm": {
                    int hour = action.getInt("hour");
                    int minute = action.getInt("minute");
                    String label = action.optString("label", "Alarm");
                    AlarmHelper.setAlarm(context, hour, minute, label);
                    return String.format("Alarm set for %02d:%02d", hour, minute);
                }
                case "wait": {
                    int ms = action.optInt("ms", 500);
                    try { Thread.sleep(Math.min(ms, 5000)); } catch (InterruptedException ignored) {}
                    return "Waited " + ms + "ms";
                }
                case "reply":
                case "done":
                    return action.optString("summary", action.optString("text", ""));
                default:
                    return "Unknown action: " + type;
            }
        } catch (JSONException e) {
            return "Malformed action (" + type + "): missing " + e.getMessage();
        } catch (SecurityException e) {
            return "Permission not granted for: " + type;
        }
    }

    private static AssistantAccessibilityService requireAccessibility() {
        AssistantAccessibilityService svc = AssistantAccessibilityService.instance;
        if (svc == null) {
            throw new SecurityException("accessibility service not enabled");
        }
        return svc;
    }
}
