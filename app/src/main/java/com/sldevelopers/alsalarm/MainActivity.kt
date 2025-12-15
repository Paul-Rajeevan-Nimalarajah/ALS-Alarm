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
    private var pendingAlarmCalendar: Calendar? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Notification permission is required.", Toast.LENGTH_LONG).show()
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

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        setListeners()
        loadSettings()
        checkAndRestoreAlarmState()
    }

    private fun setListeners() {
        alarmTimeText.setOnClickListener {
            if (setAlarmButton.text.toString().equals("Set", ignoreCase = true)) {
                showTimePickerDialog()
            }
        }

        setAlarmButton.setOnClickListener {
            if (setAlarmButton.text.toString().equals("Set", ignoreCase = true)) {
                if (pendingAlarmCalendar != null) {
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

        resetAlarmButton.setOnClickListener { cancelAlarm() }

        ringtoneButton.setOnClickListener {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            }
            ringtonePickerLauncher.launch(intent)
        }

        luxThresholdSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) luxEditText.setText(progress.toString())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                getSharedPreferences("AlarmSettings", Context.MODE_PRIVATE).edit()
                    .putInt("dismissLux", seekBar?.progress ?: 50).apply()
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
                        getSharedPreferences("AlarmSettings", Context.MODE_PRIVATE).edit()
                            .putInt("dismissLux", value).apply()
                    }
                } catch (e: NumberFormatException) {}
            }
        })

        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                getSharedPreferences("AlarmSettings", Context.MODE_PRIVATE).edit()
                    .putInt("volume", seekBar?.progress ?: 80).apply()
            }
        })

        pinEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            getSharedPreferences("AlarmSettings", Context.MODE_PRIVATE).edit().apply {
                putBoolean("pinEnabled", isChecked)
                if (!isChecked) remove("pin")
                apply()
            }
            updatePinUiState(isChecked)
        }

        for (i in 0 until dayPickerGroup.childCount) {
            (dayPickerGroup.getChildAt(i) as Chip).setOnCheckedChangeListener { _, _ -> saveSelectedDays() }
        }

        savePinButton.setOnClickListener { view ->
            if (savePinButton.text.toString().equals("Save PIN", ignoreCase = true)) {
                val pin = pinEditText.text.toString()
                if (pin.length >= 4) {
                    getSharedPreferences("AlarmSettings", Context.MODE_PRIVATE).edit()
                        .putString("pin", pin).apply()
                    Toast.makeText(this, "PIN Saved!", Toast.LENGTH_SHORT).show()
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .hideSoftInputFromWindow(view.windowToken, 0)
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

    private fun loadSettings() {
        val sharedPrefs = getSharedPreferences("AlarmSettings", Context.MODE_PRIVATE)
        luxThresholdSeekBar.progress = sharedPrefs.getInt("dismissLux", 50)
        luxEditText.setText(luxThresholdSeekBar.progress.toString())
        volumeSeekBar.progress = sharedPrefs.getInt("volume", 80)
        pinEnabledSwitch.isChecked = sharedPrefs.getBoolean("pinEnabled", false)
        updatePinUiState(pinEnabledSwitch.isChecked)

        val selectedDays = sharedPrefs.getStringSet("selectedDays", emptySet()) ?: emptySet()
        dayPickerGroup.checkedChipIds.forEach { dayPickerGroup.findViewById<Chip>(it).isChecked = false }
        selectedDays.forEach { day ->
            dayPickerGroup.findViewWithTag<Chip>(day)?.isChecked = true
        }
    }

    private fun checkAndRestoreAlarmState() {
        val sharedPrefs = getSharedPreferences("AlarmSettings", Context.MODE_PRIVATE)
        val alarmTimeMillis = sharedPrefs.getLong("alarmTimeMillis", -1L)
        if (alarmTimeMillis != -1L) {
            if (alarmTimeMillis > System.currentTimeMillis()) {
                alarmTimeText.text = sharedPrefs.getString("alarmTimeText", "No Alarm Set")
                setSettingsEnabled(false)
            } else {
                // If the alarm is in the past, but it's a repeating alarm, let the receiver handle it.
                val isRepeating = (sharedPrefs.getStringSet("selectedDays", null)?.size ?: 0) > 0
                if (!isRepeating) {
                    cancelAlarm()
                }
            }
        }
    }

    private fun saveSelectedDays() {
        val sharedPrefs = getSharedPreferences("AlarmSettings", Context.MODE_PRIVATE).edit()
        val selectedChips = mutableSetOf<String>()
        dayPickerGroup.checkedChipIds.forEach { id ->
            val chip = dayPickerGroup.findViewById<Chip>(id)
            selectedChips.add(chip.tag.toString())
        }
        sharedPrefs.putStringSet("selectedDays", selectedChips).apply()
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

        if (enabled) {
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
        if (sharedPrefs.getBoolean("pinEnabled", false) && sharedPrefs.getString("pin", null) != null) {
            val input = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }
            AlertDialog.Builder(this)
                .setTitle("Enter PIN")
                .setMessage("Enter your PIN to edit settings.")
                .setView(input)
                .setPositiveButton("OK") { _, _ ->
                    if (input.text.toString() == sharedPrefs.getString("pin", null)) {
                        setSettingsEnabled(true)
                    } else {
                        Toast.makeText(this, "Incorrect PIN!", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            setSettingsEnabled(true)
        }
    }

    private fun updatePinUiState(pinEnabled: Boolean) {
        val pinExists = getSharedPreferences("AlarmSettings", Context.MODE_PRIVATE).getString("pin", null) != null
        pinEditText.isEnabled = pinEnabled && !pinExists
        pinEditText.setText(if (pinEnabled && pinExists) "****" else "")
        pinEditText.hint = if (pinEnabled) "Enter 4+ digit PIN" else "PIN Disabled"
        savePinButton.text = if (pinEnabled && pinExists) "Change PIN" else "Save PIN"
        savePinButton.isEnabled = pinEnabled
    }

    private fun checkAndRequestPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("This app needs to draw over other apps to show the alarm screen. Please grant this permission in settings.")
                .setPositiveButton("Go to Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
                .setNegativeButton("Cancel", null).show()
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !(getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            return false
        }
        return true
    }

    private fun showTimePickerDialog() {
        val calendar = Calendar.getInstance()
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                pendingAlarmCalendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                }
                alarmTimeText.text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(pendingAlarmCalendar!!.time)
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false
        ).show()
    }

    private fun setAlarm(cal: Calendar) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val sharedPrefs = getSharedPreferences("AlarmSettings", Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()

        val selectedDays = sharedPrefs.getStringSet("selectedDays", emptySet()) ?: emptySet()
        
        val nextTriggerTime = calculateNextTriggerTime(cal, selectedDays)

        if (nextTriggerTime != -1L) {
            val intent = Intent(this, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerTime, pendingIntent)

            editor.putInt("alarmHour", cal.get(Calendar.HOUR_OF_DAY))
            editor.putInt("alarmMinute", cal.get(Calendar.MINUTE))
            editor.putLong("alarmTimeMillis", nextTriggerTime)
            editor.putString("alarmTimeText", SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.time))
            editor.apply()

            Toast.makeText(this, "Alarm set for ${SimpleDateFormat("EEE, MMM d, hh:mm a", Locale.getDefault()).format(nextTriggerTime)}", Toast.LENGTH_LONG).show()
            setSettingsEnabled(false)
        } else {
             if (selectedDays.isNotEmpty()) { // This should only be shown if repeating is intended
                 Toast.makeText(this, "Please select at least one day for the alarm to repeat.", Toast.LENGTH_LONG).show()
             }
        }
    }

    private fun calculateNextTriggerTime(cal: Calendar, selectedDays: Set<String>): Long {
        if (selectedDays.isEmpty()) { // One-time alarm
            return if (cal.timeInMillis > System.currentTimeMillis()) cal.timeInMillis else cal.apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        }

        val dayMapping = mapOf("Su" to 1, "M" to 2, "Tu" to 3, "W" to 4, "Th" to 5, "F" to 6, "Sa" to 7)
        val calendarDays = selectedDays.mapNotNull { dayMapping[it] }.sorted()
        val now = Calendar.getInstance()
        val today = now.get(Calendar.DAY_OF_WEEK)

        for (day in calendarDays) {
            if (day > today || (day == today && cal.timeInMillis > now.timeInMillis)) {
                val nextAlarm = cal.clone() as Calendar
                nextAlarm.set(Calendar.DAY_OF_WEEK, day)
                return nextAlarm.timeInMillis
            }
        }
        
        // If all selected days are past, schedule for the first selected day of next week
        return cal.apply { set(Calendar.DAY_OF_WEEK, calendarDays[0]); add(Calendar.WEEK_OF_YEAR, 1) }.timeInMillis
    }


    private fun cancelAlarm() {
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(
            PendingIntent.getBroadcast(this, 0, Intent(this, AlarmReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        )
        alarmTimeText.text = "No Alarm Set"
        pendingAlarmCalendar = null
        Toast.makeText(this, "Alarm canceled!", Toast.LENGTH_SHORT).show()

        getSharedPreferences("AlarmSettings", Context.MODE_PRIVATE).edit().apply {
            remove("alarmTimeMillis")
            remove("alarmTimeText")
            remove("selectedDays")
            remove("alarmHour")
            remove("alarmMinute")
            apply()
        }
        loadSettings()
        setSettingsEnabled(true)
    }

    override fun onResume() {
        super.onResume()
        lightSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            luxValueText.text = "Current Lux: %.2f".format(event.values[0])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
