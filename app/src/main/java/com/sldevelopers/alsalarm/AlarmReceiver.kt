package com.sldevelopers.alsalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sldevelopers.alsalarm.data.AlarmDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("alarm_id", -1)
        if (alarmId == -1) return

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("alarm_id", alarmId)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        rescheduleNextAlarm(context, alarmId)
    }

    private fun rescheduleNextAlarm(context: Context, alarmId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val alarmDao = AlarmDatabase.getDatabase(context).alarmDao()
            val alarm = alarmDao.getAlarmById(alarmId)
            if (alarm != null && alarm.selectedDays.isNotEmpty()) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    putExtra("alarm_id", alarm.id)
                }
                val pendingIntent = PendingIntent.getBroadcast(context, alarm.id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, alarm.hour)
                    set(Calendar.MINUTE, alarm.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val nextTriggerTime = calculateNextTriggerTime(cal, alarm.selectedDays)

                if (nextTriggerTime != -1L) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerTime, pendingIntent)
                }
            }
        }
    }

    private fun calculateNextTriggerTime(cal: Calendar, selectedDays: Set<String>): Long {
        if (selectedDays.isEmpty()) return -1L // This should not happen for repeating alarms

        val dayMapping = mapOf("Su" to 1, "M" to 2, "Tu" to 3, "W" to 4, "Th" to 5, "F" to 6, "Sa" to 7)
        val calendarDays = selectedDays.mapNotNull { dayMapping[it] }.toSortedSet()
        val now = Calendar.getInstance()

        val tempCal = now.clone() as Calendar
        tempCal.add(Calendar.DAY_OF_YEAR, 1) // Start checking from tomorrow

        for (i in 0..7) {
            val dayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK)
            if (dayOfWeek in calendarDays) {
                val potentialAlarm = cal.clone() as Calendar
                potentialAlarm.set(Calendar.YEAR, tempCal.get(Calendar.YEAR))
                potentialAlarm.set(Calendar.MONTH, tempCal.get(Calendar.MONTH))
                potentialAlarm.set(Calendar.DAY_OF_MONTH, tempCal.get(Calendar.DAY_OF_MONTH))
                return potentialAlarm.timeInMillis
            }
            tempCal.add(Calendar.DAY_OF_YEAR, 1)
        }

        return -1L // Should not happen
    }
}
