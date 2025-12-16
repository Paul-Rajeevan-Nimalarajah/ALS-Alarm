package com.sldevelopers.alsalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.sldevelopers.alsalarm.data.Alarm
import com.sldevelopers.alsalarm.data.AlarmDatabase

class AlarmScreenActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private lateinit var alarmViewModel: AlarmViewModel
    private var currentAlarm: Alarm? = null
    private var isSensorRegistered = false

    private lateinit var alarmLabelTextView: TextView
    private lateinit var luxContainer: LinearLayout
    private lateinit var currentLuxLabel: TextView
    private lateinit var requiredLuxLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        setContentView(R.layout.activity_alarm_screen)

        alarmLabelTextView = findViewById(R.id.alarmLabelTextView)
        luxContainer = findViewById(R.id.lux_container)
        currentLuxLabel = findViewById(R.id.current_lux_label)
        requiredLuxLabel = findViewById(R.id.required_lux_label)

        val isPreview = intent.getBooleanExtra("is_preview", false)
        val alarmId = intent.getIntExtra("alarm_id", -1)

        if (isPreview) {
            if (alarmId == -1) {
                // New alarm preview
                val hour = intent.getIntExtra("hour", 0)
                val minute = intent.getIntExtra("minute", 0)
                val label = intent.getStringExtra("label")
                val selectedDays = intent.getStringArrayListExtra("selected_days")?.toSet() ?: emptySet()
                val luxEnabled = intent.getBooleanExtra("lux_enabled", false)
                val luxDismissLevel = intent.getIntExtra("lux_dismiss_level", 0)
                val volume = intent.getIntExtra("volume", 80)
                val ringtoneUri = intent.getStringExtra("ringtone_uri")
                val pinEnabled = intent.getBooleanExtra("pin_enabled", false)
                val pin = intent.getStringExtra("pin")

                currentAlarm = Alarm(
                    hour = hour,
                    minute = minute,
                    label = label,
                    selectedDays = selectedDays,
                    isLuxDismissalEnabled = luxEnabled,
                    dismissLux = luxDismissLevel,
                    volume = volume,
                    ringtoneUri = ringtoneUri,
                    isPinEnabled = pinEnabled,
                    pin = pin
                )
                setupUI(currentAlarm!!, isPreview = true)
            } else {
                // Existing alarm preview
                loadAlarmFromDatabase(alarmId, isPreview = true)
            }
        } else {
            // Regular alarm
            loadAlarmFromDatabase(alarmId)
        }
    }

    private fun loadAlarmFromDatabase(alarmId: Int, isPreview: Boolean = false) {
        if (alarmId == -1) {
            Log.e("AlarmScreenActivity", "No alarm ID passed to activity")
            finish()
            return
        }
        val alarmDao = AlarmDatabase.getDatabase(application).alarmDao()
        val viewModelFactory = AlarmViewModelFactory(alarmDao)
        alarmViewModel = ViewModelProvider(this, viewModelFactory)[AlarmViewModel::class.java]

        alarmViewModel.getAlarm(alarmId).observe(this) { alarm ->
            if (alarm != null) {
                Log.d("AlarmScreenActivity", "Alarm data loaded: $alarm")
                currentAlarm = alarm
                setupUI(alarm, isPreview)
                registerSensorListener()
            } else {
                Log.e("AlarmScreenActivity", "Alarm with ID $alarmId not found in database")
                stopService(Intent(this, AlarmService::class.java))
                finishAndRemoveTask()
            }
        }
    }

    private fun setupUI(alarm: Alarm, isPreview: Boolean = false) {
        alarmLabelTextView.text = alarm.label
        val dismissButton = findViewById<Button>(R.id.dismissButton)
        val pinText = findViewById<EditText>(R.id.pinEditText)
        val snoozeButtonContainer = findViewById<LinearLayout>(R.id.snooze_button_container)

        if (isPreview) {
            snoozeButtonContainer.visibility = View.GONE
            dismissButton.text = "Close Preview"
            dismissButton.setOnClickListener { finish() }
        } else {
            val isSnoozed = intent.getBooleanExtra("is_snoozed", false)
            if (isSnoozed) {
                snoozeButtonContainer.visibility = View.GONE
            } else {
                snoozeButtonContainer.visibility = View.VISIBLE
                findViewById<Button>(R.id.snooze5minButton).setOnClickListener { snooze(5) }
                findViewById<Button>(R.id.snooze10minButton).setOnClickListener { snooze(10) }
                findViewById<Button>(R.id.snooze15minButton).setOnClickListener { snooze(15) }
                findViewById<Button>(R.id.snooze20minButton).setOnClickListener { snooze(20) }
            }

            dismissButton.setOnClickListener {
                if (!alarm.isPinEnabled || pinText.text.toString() == alarm.pin) {
                    stopService(Intent(this, AlarmService::class.java))
                    if (alarm.selectedDays.isEmpty()) {
                        alarm.isEnabled = false
                        alarmViewModel.update(alarm)
                    }
                    finishAndRemoveTask()
                } else {
                    Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show()
                }
            }
        }

        pinText.visibility = if (alarm.isPinEnabled) View.VISIBLE else View.GONE

        if (alarm.isLuxDismissalEnabled) {
            luxContainer.visibility = View.VISIBLE
            requiredLuxLabel.text = "Required Lux: ${alarm.dismissLux}"
            dismissButton.isEnabled = false
            if (lightSensor == null) {
                currentLuxLabel.text = "Light sensor not available"
                dismissButton.isEnabled = true
            } else {
                currentLuxLabel.text = "Current Lux: 0"
            }
        } else {
            luxContainer.visibility = View.GONE
            dismissButton.isEnabled = true
        }
    }

    private fun snooze(minutes: Int) {
        currentAlarm?.let { alarm ->
            stopService(Intent(this, AlarmService::class.java))

            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, AlarmReceiver::class.java).apply {
                putExtra("alarm_id", alarm.id)
                putExtra("is_snoozed", true)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                this,
                alarm.id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val snoozeTime = System.currentTimeMillis() + minutes * 60 * 1000

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                snoozeTime,
                pendingIntent
            )

            Toast.makeText(this, "Snoozed for $minutes minutes", Toast.LENGTH_SHORT).show()
            finishAndRemoveTask()
        }
    }

    private fun registerSensorListener() {
        if (currentAlarm?.isLuxDismissalEnabled == true && lightSensor != null && !isSensorRegistered) {
            Log.d("AlarmScreenActivity", "Registering light sensor listener")
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
            isSensorRegistered = true
        }
    }

    private fun unregisterSensorListener() {
        if (isSensorRegistered) {
            Log.d("AlarmScreenActivity", "Unregistering light sensor listener")
            sensorManager.unregisterListener(this)
            isSensorRegistered = false
        }
    }

    override fun onResume() {
        super.onResume()
        registerSensorListener()
    }

    override fun onPause() {
        super.onPause()
        unregisterSensorListener()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            val luxValue = event.values[0]
            Log.d("AlarmScreenActivity", "Sensor changed, new lux value: $luxValue")
            currentLuxLabel.text = "Current Lux: $luxValue"
            currentAlarm?.let { alarm ->
                if (alarm.isLuxDismissalEnabled) {
                    val dismissButton = findViewById<Button>(R.id.dismissButton)
                    dismissButton.isEnabled = luxValue > alarm.dismissLux
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBackPressed() {}
}