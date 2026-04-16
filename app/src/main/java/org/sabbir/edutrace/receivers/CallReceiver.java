package org.sabbir.edutrace.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.telephony.TelephonyManager;

public class CallReceiver extends BroadcastReceiver {
    public static final String ACTION_INCOMING_CALL = "org.sabbir.edutrace.INCOMING_CALL";
    public static final String ACTION_CALL_ENDED = "org.sabbir.edutrace.CALL_ENDED";
    
    private static boolean isRinging = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() != null && intent.getAction().equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            
            SharedPreferences prefs = context.getSharedPreferences("Settings", Context.MODE_PRIVATE);
            boolean libraryMode = prefs.getBoolean("library_mode", false);
            
            if (libraryMode) {
                if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
                    if (!isRinging) {
                        isRinging = true;
                        String incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
                        if (incomingNumber == null) incomingNumber = "Unknown Number";
                        
                        Intent broadcastIntent = new Intent(ACTION_INCOMING_CALL);
                        broadcastIntent.putExtra("INCOMING_NUMBER", incomingNumber);
                        context.sendBroadcast(broadcastIntent);
                    }
                } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state) || TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
                    if (isRinging) {
                        isRinging = false;
                        Intent broadcastIntent = new Intent(ACTION_CALL_ENDED);
                        context.sendBroadcast(broadcastIntent);
                    }
                }
            }
        }
    }
}
