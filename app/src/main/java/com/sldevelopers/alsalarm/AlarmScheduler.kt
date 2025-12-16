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

    fun skipNext(context: Context, alarm: Alarm): Long {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nextTriggerTime = calculateNextTriggerTime(alarm)
        if (nextTriggerTime == -1L) return 0L

        val fromTime = Calendar.getInstance()
        fromTime.timeInMillis = nextTriggerTime
        fromTime.add(Calendar.MINUTE, 1)

        val nextButOneTriggerTime = calculateNextTriggerTime(alarm, fromTime)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("alarm_id", alarm.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(context, alarm.id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        if (nextButOneTriggerTime != -1L) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextButOneTriggerTime, pendingIntent)
        } else {
            alarmManager.cancel(pendingIntent)
        }
        return nextTriggerTime
    }

    fun calculateNextTriggerTime(alarm: Alarm): Long {
        val from = Calendar.getInstance()
        if (alarm.skippedUntil > from.timeInMillis) {
            from.timeInMillis = alarm.skippedUntil
        }
        return calculateNextTriggerTime(alarm, from)
    }

    private fun calculateNextTriggerTime(alarm: Alarm, from: Calendar): Long {
        if (!alarm.isEnabled && alarm.skippedUntil == 0L) return -1L

        val fromCal = from.clone() as Calendar

        if (alarm.selectedDays.isEmpty()) { // One-time alarm
            val alarmCal = Calendar.getInstance()
            alarmCal.set(Calendar.HOUR_OF_DAY, alarm.hour)
            alarmCal.set(Calendar.MINUTE, alarm.minute)
            alarmCal.set(Calendar.SECOND, 0)
            alarmCal.set(Calendar.MILLISECOND, 0)
            if (alarmCal.timeInMillis <= fromCal.timeInMillis) {
                alarmCal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return alarmCal.timeInMillis
        }

        // Repeating alarm
        val dayMapping = mapOf("Su" to Calendar.SUNDAY, "M" to Calendar.MONDAY, "Tu" to Calendar.TUESDAY, "W" to Calendar.WEDNESDAY, "Th" to Calendar.THURSDAY, "F" to Calendar.FRIDAY, "Sa" to Calendar.SATURDAY)
        val selectedCalDays = alarm.selectedDays.mapNotNull { dayMapping[it] }.toSet()

        val tempCal = fromCal.clone() as Calendar
        tempCal.set(Calendar.HOUR_OF_DAY, alarm.hour)
        tempCal.set(Calendar.MINUTE, alarm.minute)
        tempCal.set(Calendar.SECOND, 0)
        tempCal.set(Calendar.MILLISECOND, 0)

        if (tempCal.timeInMillis > fromCal.timeInMillis && tempCal.get(Calendar.DAY_OF_WEEK) in selectedCalDays) {
            return tempCal.timeInMillis
        }

        tempCal.add(Calendar.DAY_OF_YEAR, 1)
        tempCal.set(Calendar.HOUR_OF_DAY, alarm.hour)
        tempCal.set(Calendar.MINUTE, alarm.minute)

        for (i in 0..7) {
            val dayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK)
            if (dayOfWeek in selectedCalDays) {
                return tempCal.timeInMillis
            }
            tempCal.add(Calendar.DAY_OF_YEAR, 1)
        }

        return -1L
    }
}
