package com.dhanaram.assistanttwin.actions;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.SmsManager;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

public class MessagingHelper {

    /** Sends a real SMS directly -- requires SEND_SMS to already be granted. */
    public static boolean sendSms(Context context, String phoneNumber, String message) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        SmsManager smsManager = context.getSystemService(SmsManager.class);
        ArrayList<String> parts = smsManager.divideMessage(message);
        smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null);
        return true;
    }

    // Sending via WhatsApp/Telegram/any other app with no public send API is done
    // by driving the UI through AgentActionExecutor's tap_text/type_text primitives
    // instead -- see AgentLoop for the orchestration.
}
