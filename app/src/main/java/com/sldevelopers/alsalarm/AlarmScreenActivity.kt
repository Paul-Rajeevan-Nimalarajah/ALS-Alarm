package com.sldevelopers.alsalarm

import android.content.*
import android.hardware.*
import android.os.*
import android.util.Log
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
        Log.d("AlarmScreenActivity", "onCreate: Activity starting")

        try {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
            Log.d("AlarmScreenActivity", "onCreate: Window flags set")

            setContentView(R.layout.activity_alarm_screen)
            Log.d("AlarmScreenActivity", "onCreate: Content view set")

            val dismiss = findViewById<Button>(R.id.dismissButton)
            val pinText = findViewById<EditText>(R.id.pinEditText)
            Log.d("AlarmScreenActivity", "onCreate: Views found")

            val prefs = getSharedPreferences("AlarmSettings", MODE_PRIVATE)
            luxLimit = prefs.getInt("dismissLux", 50)
            pinEnabled = prefs.getBoolean("pinEnabled", false)
            pin = prefs.getString("pin", null)
            Log.d("AlarmScreenActivity", "onCreate: Preferences loaded. pinEnabled: $pinEnabled")

            pinText.visibility = if (pinEnabled) View.VISIBLE else View.GONE
            dismiss.isEnabled = false
            Log.d("AlarmScreenActivity", "onCreate: UI state initialized")

            sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
            Log.d("AlarmScreenActivity", "onCreate: Sensor manager initialized. Light sensor found: ${lightSensor != null}")

            dismiss.setOnClickListener {
                if (!pinEnabled || pinText.text.toString() == pin) {
                    stopService(Intent(this, AlarmService::class.java))
                    finishAndRemoveTask()
                } else {
                    Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show()
                }
            }
            Log.d("AlarmScreenActivity", "onCreate: OnClickListener set")

        } catch (e: Exception) {
            Log.e("AlarmScreenActivity", "FATAL CRASH IN ONCREATE", e)
            // We still want to stop the alarm sound if the UI crashes
            try {
                stopService(Intent(this, AlarmService::class.java))
            } catch (serviceException: Exception) {
                Log.e("AlarmScreenActivity", "Could not stop service after crash", serviceException)
            }
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("AlarmScreenActivity", "onResume: Registering sensor listener")
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d("AlarmScreenActivity", "onPause: Unregistering sensor listener")
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            try {
                findViewById<Button>(R.id.dismissButton).isEnabled =
                    event.values[0] > luxLimit
            } catch (e: Exception) {
                // Avoid crashing in a frequently called method
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBackPressed() {}
}
