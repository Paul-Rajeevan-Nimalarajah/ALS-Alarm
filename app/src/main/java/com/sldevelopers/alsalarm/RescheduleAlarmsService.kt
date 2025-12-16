package com.sldevelopers.alsalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.sldevelopers.alsalarm.data.Alarm
import com.sldevelopers.alsalarm.data.AlarmDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class RescheduleAlarmsService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val alarmDao = AlarmDatabase.getDatabase(application).alarmDao()

        CoroutineScope(Dispatchers.IO).launch {
            val alarms = alarmDao.getAllAlarms().value
            alarms?.forEach { alarm ->
                if (alarm.isEnabled) {
                    rescheduleAlarm(alarm)
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun rescheduleAlarm(alarm: Alarm) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("alarm_id", alarm.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
        }

        val nextTriggerTime = calculateNextTriggerTime(calendar, alarm.selectedDays)

        if (nextTriggerTime != -1L) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerTime, pendingIntent)
        }
    }

    private fun calculateNextTriggerTime(cal: Calendar, selectedDays: Set<String>): Long {
        if (selectedDays.isEmpty()) { // One-time alarm
            return if (cal.timeInMillis > System.currentTimeMillis()) cal.timeInMillis else -1L // Don't reschedule past one-time alarms
        }

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
