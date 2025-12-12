package com.example.alsalarm

import android.app.NotificationManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.alsalarm.ui.theme.ALSAlarmTheme

class AlarmScreenActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private var ringtone: Ringtone? = null
    private var dismissLuxValue: Float = 50f
    private var currentLuxValue by mutableStateOf("Listening...")
    private var isArmed by mutableStateOf(false) // The new arming state

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make the activity full-screen
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )

        val sharedPrefs = getSharedPreferences("AlarmSettings", Context.MODE_PRIVATE)
        dismissLuxValue = sharedPrefs.getFloat("dismissLux", 50f)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        try {
            var alarmUri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
            ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)
            ringtone?.isLooping = true // Ensure the ringtone loops
            ringtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            ALSAlarmTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    FullScreenAlarmUI()
                }
            }
        }
    }

    @Composable
    fun FullScreenAlarmUI() {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("ALARM!", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(32.dp))
            if (!isArmed) {
                Text("Place in darkness to arm dismissal", fontSize = 24.sp)
            } else {
                Text("Armed! Expose to light to dismiss.", fontSize = 24.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Current Lux: $currentLuxValue", fontSize = 24.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Dismiss at ${dismissLuxValue.toInt()} Lux", fontSize = 24.sp)
        }
    }

    override fun onResume() {
        super.onResume()
        if (lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            currentLuxValue = "Not available"
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            val lux = event.values[0]
            currentLuxValue = "%.2f".format(lux)

            if (!isArmed) {
                // Wait for the lux value to go low to arm the alarm
                if (lux < 10f) {
                    isArmed = true
                }
            } else {
                // Once armed, wait for the lux value to go high to dismiss
                if (lux > dismissLuxValue) {
                    ringtone?.stop()

                    // THE FIX: Cancel the notification when the alarm is dismissed
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(1) // The ID must match the one in AlarmReceiver

                    finishAndRemoveTask()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBackPressed() {
        // Prevent user from dismissing the alarm with the back button
    }
}