package com.sldevelopers.alsalarm

import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        setContentView(R.layout.activity_alarm_screen)

        val alarmId = intent.getIntExtra("alarm_id", -1)
        if (alarmId == -1) {
            finish()
            return
        }

        val alarmDao = AlarmDatabase.getDatabase(application).alarmDao()
        val viewModelFactory = AlarmViewModelFactory(alarmDao)
        alarmViewModel = ViewModelProvider(this, viewModelFactory)[AlarmViewModel::class.java]

        alarmViewModel.getAlarm(alarmId).observe(this) { alarm ->
            if (alarm != null) {
                currentAlarm = alarm
                setupUI(alarm)
            } else {
                // If alarm is not found, dismiss the screen
                stopService(Intent(this, AlarmService::class.java))
                finishAndRemoveTask()
            }
        }
    }

    private fun setupUI(alarm: Alarm) {
        val dismissButton = findViewById<Button>(R.id.dismissButton)
        val pinText = findViewById<EditText>(R.id.pinEditText)

        pinText.visibility = if (alarm.isPinEnabled) View.VISIBLE else View.GONE

        if (alarm.isLuxDismissalEnabled) {
            dismissButton.isEnabled = false
            sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
            lightSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
                isSensorRegistered = true
            }
        } else {
            dismissButton.isEnabled = true
            lightSensor = null
        }

        dismissButton.setOnClickListener {
            if (!alarm.isPinEnabled || pinText.text.toString() == alarm.pin) {
                stopService(Intent(this, AlarmService::class.java))
                finishAndRemoveTask()
            } else {
                Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (isSensorRegistered) {
            sensorManager.unregisterListener(this)
            isSensorRegistered = false
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            currentAlarm?.let { alarm ->
                if (alarm.isLuxDismissalEnabled) {
                    val dismissButton = findViewById<Button>(R.id.dismissButton)
                    dismissButton.isEnabled = event.values[0] > alarm.dismissLux
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBackPressed() {}
}
