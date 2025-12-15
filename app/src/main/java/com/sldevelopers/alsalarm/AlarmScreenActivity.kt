package com.sldevelopers.alsalarm

import android.content.*
import android.hardware.*
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class AlarmScreenActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private var luxLimit = 50
    private var pin: String? = null
    private var pinEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        setContentView(R.layout.activity_alarm_screen)

        val dismiss = findViewById<Button>(R.id.dismissButton)
        val pinText = findViewById<EditText>(R.id.pinEditText)

        val prefs = getSharedPreferences("AlarmSettings", MODE_PRIVATE)
        luxLimit = prefs.getInt("dismissLux", 50)
        pinEnabled = prefs.getBoolean("pinEnabled", false)
        pin = prefs.getString("pin", null)

        pinText.visibility = if (pinEnabled) View.VISIBLE else View.GONE
        dismiss.isEnabled = false

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        dismiss.setOnClickListener {
            if (!pinEnabled || pinText.text.toString() == pin) {
                stopService(Intent(this, AlarmService::class.java))
                finishAndRemoveTask()
            } else {
                Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            findViewById<Button>(R.id.dismissButton).isEnabled =
                event.values[0] > luxLimit
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBackPressed() {}
}
