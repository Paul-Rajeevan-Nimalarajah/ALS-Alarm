package com.example.alsalarm

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.alsalarm.ui.theme.ALSAlarmTheme
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ALSAlarmTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AlarmScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmScreen(modifier: Modifier = Modifier) {
    var showTimePicker by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState()
    var luxValue by remember { mutableStateOf("Listening...") }
    var alarmTimeText by remember { mutableStateOf("No Alarm Set") }
    val context = LocalContext.current
    val formatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val sharedPrefs = remember { context.getSharedPreferences("AlarmSettings", Context.MODE_PRIVATE) }
    
    var sliderPosition by remember { mutableStateOf(sharedPrefs.getFloat("dismissLux", 50f)) }

    val alarmManager = remember { context.getSystemService(Context.ALARM_SERVICE) as AlarmManager }
    var timeToSet by remember { mutableStateOf<Calendar?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                Toast.makeText(context, "Notification permission granted! Setting alarm.", Toast.LENGTH_SHORT).show()
                timeToSet?.let { setAlarm(context, it) }
            } else {
                Toast.makeText(context, "Notification permission is required to set alarms.", Toast.LENGTH_LONG).show()
            }
        }
    )

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
                    luxValue = "%.2f".format(event.values[0])
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (lightSensor != null) {
            sensorManager.registerListener(sensorListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            luxValue = "Not available"
        }

        onDispose {
            sensorManager.unregisterListener(sensorListener)
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(alarmTimeText, fontSize = 48.sp)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { showTimePicker = true }) {
            Text("Set Alarm")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Current Lux: $luxValue")
        Spacer(modifier = Modifier.height(32.dp))
        Text("Dismiss alarm at ${sliderPosition.toInt()} Lux")
        Slider(
            value = sliderPosition,
            onValueChange = { sliderPosition = it },
            valueRange = 0f..1000f,
            onValueChangeFinished = {
                with(sharedPrefs.edit()) {
                    putFloat("dismissLux", sliderPosition)
                    apply()
                }
            }
        )

        if (showTimePicker) {
            TimePickerDialog(
                onCancel = { showTimePicker = false },
                onConfirm = {
                    showTimePicker = false
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    cal.set(Calendar.MINUTE, timePickerState.minute)
                    cal.set(Calendar.SECOND, 0)

                    if (cal.timeInMillis <= System.currentTimeMillis()) {
                        cal.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                        Toast.makeText(context, "ACTION REQUIRED: Please grant alarm permission.", Toast.LENGTH_LONG).show()
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).also { context.startActivity(it) }
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            Toast.makeText(context, "Requesting notification permission...", Toast.LENGTH_SHORT).show()
                            timeToSet = cal
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            setAlarm(context, cal)
                            alarmTimeText = formatter.format(cal.time)
                        }
                    }
                },
            ) {
                TimePicker(state = timePickerState)
            }
        }
    }
}

private fun setAlarm(context: Context, cal: Calendar) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, AlarmReceiver::class.java)
    
    // THE DEFINITIVE FIX: Use FLAG_UPDATE_CURRENT to ensure the alarm is always updated correctly.
    // This requires the PendingIntent to be mutable.
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        0, 
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )

    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
    
    val formatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
    Toast.makeText(context, "Alarm set for ${formatter.format(cal.time)}!", Toast.LENGTH_SHORT).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    onCancel: () -> Unit, 
    onConfirm: () -> Unit, 
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                content()
                Row(modifier = Modifier.height(40.dp).fillMaxWidth()) {
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(16.dp))
                    TextButton(onClick = onConfirm) { Text("OK") }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AlarmScreenPreview() {
    ALSAlarmTheme {
        AlarmScreen()
    }
}
