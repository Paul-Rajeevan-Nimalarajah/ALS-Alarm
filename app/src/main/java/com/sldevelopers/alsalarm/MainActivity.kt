package com.sldevelopers.alsalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private lateinit var alarmTimeText: TextView
    private lateinit var luxValueText: TextView
    private lateinit var luxEditText: EditText
    private lateinit var luxThresholdSeekBar: SeekBar
    private lateinit var volumeSeekBar: SeekBar
    private lateinit var ringtoneButton: Button
    private lateinit var pinEnabledSwitch: SwitchMaterial
    private lateinit var dayPickerGroup: ChipGroup
    private lateinit var pinEditText: EditText
    private lateinit var savePinButton: Button
    private lateinit var setAlarmButton: Button
    private lateinit var resetAlarmButton: Button
    private var pendingAlarmCalendar: Calendar? = null // Holds the selected time before setting

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Notification permission is required for alarms.", Toast.LENGTH_LONG).show()
        }
    }

    private val ringtonePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                val sharedPrefs = getSharedPreferences("AlarmSettings", Context.MODE_PRIVATE)
                with(sharedPrefs.edit()) {
                    putString("ringtone_uri", uri.toString())
                    apply()
                }
                Toast.makeText(this, "Ringtone updated!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_main)

        // Initialize all views
        alarmTimeText = findViewById(R.id.alarmTimeText)
        luxValueText = findViewById(R.id.luxValueText)
        luxEditText = findViewById(R.id.luxEditText)
        luxThresholdSeekBar = findViewById(R.id.luxThresholdSeekBar)
        volumeSeekBar = findViewById(R.id.volumeSeekBar)
        ringtoneButton = findViewById(R.id.ringtoneButton)
        pinEnabledSwitch = findViewById(R.id.pinEnabledSwitch)
        dayPickerGroup = findViewById(R.id.dayPickerGroup)
        pinEditText = findViewById(R.id.pinEditText)
        setAlarmButton = findViewById(R.id.setAlarmButton)
        resetAlarmButton = findViewById(R.id.setAlarmButton2)
        savePinButton = findViewById(R.id.savePinButton)

        // Sensor initialization
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        // Set listeners
        alarmTimeText.setOnClickListener {
            if (setAlarmButton.text.toString().equals("Set", ignoreCase = true)) {
                showTimePickerDialog()
            }
        }

        setAlarmButton.setOnClickListener {
            if (setAlarmButton.text.toString().equals("Set", ignoreCase = true)) {
                val sharedPrefs = getSharedPreferences("AlarmSettings", Context.MODE_PRIVATE)
                val pinEnabled = sharedPrefs.getBoolean("pinEnabled", false)
                val pinSaved = sharedPrefs.getString("pin", null) != null

                if (pinEnabled && !pinSaved) {
                    Toast.makeText(this, "Please save a PIN before setting the alarm.", Toast.LENGTH_LONG).show()
                } else if (pendingAlarmCalendar != null) {
                    if (checkAndRequestPermissions()) {
                        setAlarm(pendingAlarmCalendar!!)
                    }
                } else {
                    Toast.makeText(this, "Please select a time first.", Toast.LENGTH_SHORT).show()
                }
            } else { // "Edit"
                handleEditSettings()
            }
        }

        resetAlarmButton.setOnClickListener {
            cancelAlarm()
        }

        ringtoneButton.setOnClickListener {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            ringtonePickerLauncher.launch(intent)
        }

        // Load and apply saved settings
        val sharedPrefs = getSharedPreferences("AlarmSettings", Context.MODE_PRIVATE)
        val savedLux = sharedPrefs.getInt("dismissLux", 50)
        luxThresholdSeekBar.progress = savedLux
        luxEditText.setText(savedLux.toString())

        val savedVolume = sharedPrefs.getInt("volume", 80)
        volumeSeekBar.progress = savedVolume

        val pinEnabled = sharedPrefs.getBoolean("pinEnabled", false)
        pinEnabledSwitch.isChecked = pinEnabled
        updatePinUiState(pinEnabled)

        val selectedDays = sharedPrefs.getStringSet("selectedDays", emptySet()) ?: emptySet()
        for (i in 0 until dayPickerGroup.childCount) {
            val chip = dayPickerGroup.getChildAt(i) as Chip
            if (selectedDays.contains(chip.text.toString())) {
                chip.isChecked = true
            }
        }

        // Restore alarm time text and UI state if a valid alarm is set
        val alarmTimeMillis = sharedPrefs.getLong("alarmTimeMillis", -1L)
        if (alarmTimeMillis != -1L && alarmTimeMillis > System.currentTimeMillis()) {
            val alarmTimeTextString = sharedPrefs.getString("alarmTimeText", "No Alarm Set")
            alarmTimeText.text = alarmTimeTextString
            setSettingsEnabled(false) // Lock the UI if alarm is active
        } else if (alarmTimeMillis != -1L) {
            cancelAlarm()
        }

        // --- Syncing Logic for Lux and Volume ---
        luxThresholdSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    luxEditText.setText(progress.toString())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                with(sharedPrefs.edit()) {
                    putInt("dismissLux", seekBar?.progress ?: 50)
                    apply()
                }
            }
        })

        luxEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                try {
                    val value = s.toString().toInt()
                    if (value in 0..1000) {
                        luxThresholdSeekBar.progress = value
                        with(sharedPrefs.edit()) {
                            putInt("dismissLux", value)
                            apply()
                        }
                    }
                } catch (e: NumberFormatException) {
                    // Ignore
                }
            }
        })

        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                with(sharedPrefs.edit()) {
                    putInt("volume", seekBar?.progress ?: 80)
                    apply()
                }
            }
        })

        // --- Definitive PIN Logic ---
        pinEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            val editor = sharedPrefs.edit()
            editor.putBoolean("pinEnabled", isChecked)
            if (!isChecked) {
                editor.remove("pin")
            }
            editor.apply()
            updatePinUiState(isChecked)
        }

        dayPickerGroup.setOnCheckedChangeListener { group, checkedId ->
            saveSelectedDays()
        }

        savePinButton.setOnClickListener { view ->
            if (savePinButton.text.toString().equals("Save PIN", ignoreCase = true)) {
                val pin = pinEditText.text.toString()
                if (pin.length >= 4) {
                    with(sharedPrefs.edit()) {
                        putString("pin", pin)
                        apply()
                    }
                    Toast.makeText(this, "PIN Saved!", Toast.LENGTH_SHORT).show()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(view.windowToken, 0)
                    pinEditText.clearFocus()
                    updatePinUiState(pinEnabledSwitch.isChecked)
                } else {
                    Toast.makeText(this, "PIN must be at least 4 digits.", Toast.LENGTH_SHORT).show()
                }
            } else { // "Change PIN"
                pinEditText.isEnabled = true
                pinEditText.text.clear()
                pinEditText.requestFocus()
                savePinButton.text = "Save PIN"
            }
        }
    }

    private fun saveSelectedDays() {
        val sharedPrefs = getSharedPreferences("AlarmSettings", Context.MODE_PRIVATE)
        val selectedChips = mutableSetOf<String>()
        for (i in 0 until dayPickerGroup.childCount) {
            val chip = dayPickerGroup.getChildAt(i) as Chip
            if (chip.isChecked) {
                selectedChips.add(chip.text.toString())
            }
        }
        with(sharedPrefs.edit()) {
            putStringSet("selectedDays", selectedChips)
            apply()
        }
    }

    private fun setSettingsEnabled(enabled: Boolean) {
        luxEditText.isEnabled = enabled
        luxThresholdSeekBar.isEnabled = enabled
        volumeSeekBar.isEnabled = enabled
        ringtoneButton.isEnabled = enabled
        pinEnabledSwitch.isEnabled = enabled
        for (i in 0 until dayPickerGroup.childCount) {
            dayPickerGroup.getChildAt(i).isEnabled = enabled
        }
        resetAlarmButton.isEnabled = enabled

        if(enabled) {
            setAlarmButton.text = "Set"
            updatePinUiState(pinEnabledSwitch.isChecked)
        } else {
            setAlarmButton.text = "Edit"
            savePinButton.isEnabled = false
            pinEditText.isEnabled = false
        }
    }
    
    private fun handleEditSettings() {
        val sharedPrefs = getSharedPreferences("AlarmSettings", Context.MODE_PRIVATE)
        val isPinEnabled = sharedPrefs.getBoolean("pinEnabled", false)
        val correctPin = sharedPrefs.getString("pin", null)

        if (isPinEnabled && correctPin != null) {
            val input = EditText(this)
            input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            AlertDialog.Builder(this)
                .setTitle("Enter PIN")
                .setMessage("Enter your PIN to edit settings.")
                .setView(input)
                .setPositiveButton("OK") { dialog, _ ->
                    if (input.text.toString() == correctPin) {
                        setSettingsEnabled(true)
                    } else {
                        Toast.makeText(this, "Incorrect PIN!", Toast.LENGTH_SHORT).show()
                    }
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
                .show()
        } else {
            setSettingsEnabled(true)
        }
    }

    private fun updatePinUiState(pinEnabled: Boolean) {
        val sharedPrefs = getSharedPreferences("AlarmSettings", Context.MODE_PRIVATE)
        if (pinEnabled) {
            val pinExists = sharedPrefs.getString("pin", null) != null
            if (pinExists) {
                pinEditText.isEnabled = false
                pinEditText.setText("****")
                savePinButton.text = "Change PIN"
                savePinButton.isEnabled = true
            } else {
                pinEditText.isEnabled = true
                pinEditText.text.clear()
                pinEditText.hint = "Enter 4+ digit PIN"
                savePinButton.text = "Save PIN"
                savePinButton.isEnabled = true
            }
        } else {
            pinEditText.isEnabled = false
            pinEditText.text.clear()
            pinEditText.hint = "PIN Disabled"
            savePinButton.isEnabled = false
            savePinButton.text = "Save PIN"
        }
    }

    private fun checkAndRequestPermissions(): Boolean {
        // Check for "Appear on Top" permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("This app needs permission to appear on top to display the alarm screen. Please grant this permission in the app settings.")
                .setPositiveButton("Go to Settings") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
            return false
        }

        // Check for "Schedule Exact Alarm" permission
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Intent().also { intent ->
                    intent.action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                    startActivity(intent)
                }
                return false
            }
        }

        // Check for "Post Notifications" permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                return false
            }
        }

        return true
    }

    private fun showTimePickerDialog() {
        val calendar = Calendar.getInstance()
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                cal.set(Calendar.MINUTE, minute)
                cal.set(Calendar.SECOND, 0)

                pendingAlarmCalendar = cal
                val formatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
                alarmTimeText.text = formatter.format(cal.time)
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false
        ).show()
    }

    private fun setAlarm(cal: Calendar) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sharedPrefs = getSharedPreferences("AlarmSettings", Context.MODE_PRIVATE)
        val selectedDays = sharedPrefs.getStringSet("selectedDays", emptySet()) ?: emptySet()

        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        if (selectedDays.isNotEmpty()) {
            // Schedule repeating alarm for the selected days
            val dayMapping = mapOf("S" to Calendar.SUNDAY, "M" to Calendar.MONDAY, "T" to Calendar.TUESDAY, "W" to Calendar.WEDNESDAY, "Th" to Calendar.THURSDAY, "F" to Calendar.FRIDAY, "Sa" to Calendar.SATURDAY)
            val calendarDays = selectedDays.mapNotNull { dayMapping[it] }

            for (day in calendarDays) {
                val alarmCal = cal.clone() as Calendar
                alarmCal.set(Calendar.DAY_OF_WEEK, day)
                
                if (alarmCal.timeInMillis < System.currentTimeMillis()) {
                    alarmCal.add(Calendar.WEEK_OF_YEAR, 1)
                }

                val dailyIntent = Intent(this, AlarmReceiver::class.java)
                val dailyPendingIntent = PendingIntent.getBroadcast(this, day, dailyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, alarmCal.timeInMillis, AlarmManager.INTERVAL_DAY * 7, dailyPendingIntent)
            }

        } else {
            // Schedule a single, one-time alarm
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
        }

        val formatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val formattedTime = formatter.format(cal.time)
        alarmTimeText.text = formattedTime
        Toast.makeText(this, "Alarm set for $formattedTime!", Toast.LENGTH_SHORT).show()

        with(sharedPrefs.edit()) {
            putLong("alarmTimeMillis", cal.timeInMillis)
            putString("alarmTimeText", formattedTime)
            apply()
        }
        
        setSettingsEnabled(false) // Lock the UI
    }

    private fun cancelAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java)

        // Cancel all potential daily alarms
        for (day in 1..7) {
            val dailyPendingIntent = PendingIntent.getBroadcast(this, day, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            alarmManager.cancel(dailyPendingIntent)
        }

        // Also cancel the single, non-repeating alarm
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(pendingIntent)

        alarmTimeText.text = "No Alarm Set"
        pendingAlarmCalendar = null
        Toast.makeText(this, "Alarm canceled!", Toast.LENGTH_SHORT).show()

        val sharedPrefs = getSharedPreferences("AlarmSettings", Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            remove("alarmTimeMillis")
            remove("alarmTimeText")
            apply()
        }
        setSettingsEnabled(true) // Unlock UI
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
            val currentLux = event.values[0]
            luxValueText.text = "Current Lux: %.2f".format(currentLux)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
