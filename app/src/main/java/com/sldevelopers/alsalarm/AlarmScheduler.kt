package com.sldevelopers.alsalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.sldevelopers.alsalarm.data.Alarm
import java.util.Calendar

object AlarmScheduler {

    fun schedule(context: Context, alarm: Alarm) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nextTriggerTime = calculateNextTriggerTime(alarm)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("alarm_id", alarm.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(context, alarm.id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        if (nextTriggerTime != -1L) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerTime, pendingIntent)
        } else {
            alarmManager.cancel(pendingIntent) // Cancel if no valid future time
        }
    }

    fun cancel(context: Context, alarm: Alarm) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(context, alarm.id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(pendingIntent)
    }

    fun calculateNextTriggerTime(alarm: Alarm): Long {
        if (!alarm.isEnabled) return -1L

        val now = Calendar.getInstance()
        val alarmTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (alarm.selectedDays.isEmpty()) { // One-time alarm
            if (alarmTime.timeInMillis <= now.timeInMillis) {
                alarmTime.add(Calendar.DAY_OF_YEAR, 1)
            }
            return alarmTime.timeInMillis
        }

        // Repeating alarm
        val dayMapping = mapOf("Su" to Calendar.SUNDAY, "M" to Calendar.MONDAY, "Tu" to Calendar.TUESDAY, "W" to Calendar.WEDNESDAY, "Th" to Calendar.THURSDAY, "F" to Calendar.FRIDAY, "Sa" to Calendar.SATURDAY)
        val selectedDays = alarm.selectedDays.mapNotNull { dayMapping[it] }

        val tempCal = now.clone() as Calendar
        for (i in 0..7) { // Check the next 7 days (including today)
            val dayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK)
            if (dayOfWeek in selectedDays) {
                val potentialAlarm = alarmTime.clone() as Calendar
                potentialAlarm.set(Calendar.YEAR, tempCal.get(Calendar.YEAR))
                potentialAlarm.set(Calendar.MONTH, tempCal.get(Calendar.MONTH))
                potentialAlarm.set(Calendar.DAY_OF_MONTH, tempCal.get(Calendar.DAY_OF_MONTH))

                if (potentialAlarm.timeInMillis > now.timeInMillis) {
                    return potentialAlarm.timeInMillis
                }
            }
            tempCal.add(Calendar.DAY_OF_YEAR, 1)
        }

        return -1L // Should not happen with repeating alarms
    }
}