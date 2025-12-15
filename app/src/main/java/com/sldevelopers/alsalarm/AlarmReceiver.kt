package com.sldevelopers.alsalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Definitive Logging - Step 1
        Log.d("ALARM_DEBUG", "--------------------------------------")
        Log.d("ALARM_DEBUG", "AlarmReceiver: Broadcast Received! The alarm is firing.")
        Log.d("ALARM_DEBUG", "--------------------------------------")
        Toast.makeText(context, "ALARM RECEIVED BY RECEIVER", Toast.LENGTH_LONG).show()

        val serviceIntent = Intent(context, AlarmService::class.java)
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d("ALARM_DEBUG", "AlarmReceiver: startForegroundService command issued successfully.")
        } catch (e: Exception) {
            Log.e("ALARM_DEBUG", "AlarmReceiver: FAILED to start service!", e)
            Toast.makeText(context, "Error: Could not start alarm service. Check logs.", Toast.LENGTH_LONG).show()
        }
    }
}
