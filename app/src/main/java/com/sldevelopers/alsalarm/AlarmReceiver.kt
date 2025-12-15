package com.sldevelopers.alsalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Start the alarm service to play the sound
        val serviceIntent = Intent(context, AlarmService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // Reschedule the next alarm if it's a repeating one
        rescheduleNextAlarm(context)
    }

    private fun rescheduleNextAlarm(context: Context) {
        val sharedPrefs = context.getSharedPreferences("AlarmSettings", Context.MODE_PRIVATE)
        val selectedDays = sharedPrefs.getStringSet("selectedDays", emptySet()) ?: emptySet()
        val hour = sharedPrefs.getInt("alarmHour", -1)
        val minute = sharedPrefs.getInt("alarmMinute", -1)

        if (selectedDays.isEmpty() || hour == -1 || minute == -1) {
            return // Not a repeating alarm
        }

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val nextTriggerTime = calculateNextTriggerTime(cal, selectedDays)

        if (nextTriggerTime != -1L) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerTime, pendingIntent)

            // Save the next trigger time
            sharedPrefs.edit().putLong("alarmTimeMillis", nextTriggerTime).apply()
        }
    }

    private fun calculateNextTriggerTime(cal: Calendar, selectedDays: Set<String>): Long {
        if (selectedDays.isEmpty()) return -1L

        val dayMapping = mapOf("Su" to 1, "M" to 2, "Tu" to 3, "W" to 4, "Th" to 5, "F" to 6, "Sa" to 7)
        val calendarDays = selectedDays.mapNotNull { dayMapping[it] }.sorted()
        val now = Calendar.getInstance()

        val baseAlarmTime = cal.clone() as Calendar

        for (i in 0..7) { // Check the next 7 days
            val nextDay = now.clone() as Calendar
            nextDay.add(Calendar.DAY_OF_YEAR, i)
            val dayOfWeek = nextDay.get(Calendar.DAY_OF_WEEK)

            if (dayOfWeek in calendarDays) {
                val nextAlarm = baseAlarmTime.clone() as Calendar
                nextAlarm.set(Calendar.YEAR, nextDay.get(Calendar.YEAR))
                nextAlarm.set(Calendar.MONTH, nextDay.get(Calendar.MONTH))
                nextAlarm.set(Calendar.DAY_OF_MONTH, nextDay.get(Calendar.DAY_OF_MONTH))

                if (nextAlarm.timeInMillis > now.timeInMillis) {
                    return nextAlarm.timeInMillis
                }
            }
        }

        return -1L // Should not happen
    }
}
