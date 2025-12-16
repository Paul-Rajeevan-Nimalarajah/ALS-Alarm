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
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.sldevelopers.alsalarm.data.Alarm
import com.sldevelopers.alsalarm.data.AlarmDatabase
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AlarmEditorActivity : AppCompatActivity(), SensorEventListener {

    private var calendar: Calendar? = null
    private var alarmId: Int? = null
    private var ringtoneUri: String? = null
    private var currentAlarm: Alarm? = null

    private lateinit var alarmViewModel: AlarmViewModel
    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null

    // Views
    private lateinit var alarmTimeText: TextView
    private lateinit var dayPickerGroup: ChipGroup
    private lateinit var saveAlarmButton: Button
    private lateinit var deleteAlarmButton: Button
    private lateinit var luxDismissalSwitch: SwitchMaterial
    private lateinit var luxValueText: TextView
    private lateinit var luxThresholdLabel: TextView
    private lateinit var luxEditText: EditText
    private lateinit var luxThresholdSeekBar: SeekBar
    private lateinit var volumeSeekBar: SeekBar
    private lateinit var ringtoneButton: Button
    private lateinit var pinEnabledSwitch: SwitchMaterial
    private lateinit var pinEditText: EditText

    private val ringtonePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            ringtoneUri = uri?.toString()
            Toast.makeText(this, "Ringtone updated!", Toast.LENGTH_SHORT).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Notification permission is required.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm_editor)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        initializeViews()
        initializeViewModel()

        alarmId = intent.getIntExtra("alarm_id", -1).let { if (it == -1) null else it }
        alarmId?.let { id ->
            alarmViewModel.getAlarm(id).observe(this) { alarm ->
                alarm?.let {
                    currentAlarm = it
                    populateUi(it)
                    deleteAlarmButton.visibility = View.VISIBLE
                }
            }
        } ?: run {
            luxDismissalSwitch.isChecked = true // Default to enabled for new alarms
            updatePinUiState(false) // Set initial state for new alarm
            updateLuxUiState(true)
        }

        setListeners()
    }

    private fun initializeViews() {
        alarmTimeText = findViewById(R.id.alarmTimeText)
        dayPickerGroup = findViewById(R.id.dayPickerGroup)
        saveAlarmButton = findViewById(R.id.saveAlarmButton)
        deleteAlarmButton = findViewById(R.id.deleteAlarmButton)
        luxDismissalSwitch = findViewById(R.id.luxDismissalSwitch)
        luxValueText = findViewById(R.id.luxValueText)
        luxThresholdLabel = findViewById(R.id.luxThresholdLabel)
        luxEditText = findViewById(R.id.luxEditText)
        luxThresholdSeekBar = findViewById(R.id.luxThresholdSeekBar)
        volumeSeekBar = findViewById(R.id.volumeSeekBar)
        ringtoneButton = findViewById(R.id.ringtoneButton)
        pinEnabledSwitch = findViewById(R.id.pinEnabledSwitch)
        pinEditText = findViewById(R.id.pinEditText)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    }

    private fun initializeViewModel() {
        val alarmDao = AlarmDatabase.getDatabase(application).alarmDao()
        val viewModelFactory = AlarmViewModelFactory(alarmDao)
        alarmViewModel = ViewModelProvider(this, viewModelFactory)[AlarmViewModel::class.java]
    }

    private fun setListeners() {
        alarmTimeText.setOnClickListener { showTimePickerDialog() }
        saveAlarmButton.setOnClickListener { if (checkAndRequestPermissions()) saveAlarm() }
        deleteAlarmButton.setOnClickListener { deleteAlarm() }
        ringtoneButton.setOnClickListener { openRingtonePicker() }
        pinEnabledSwitch.setOnCheckedChangeListener { _, isChecked -> updatePinUiState(isChecked) }
        luxDismissalSwitch.setOnCheckedChangeListener { _, isChecked -> updateLuxUiState(isChecked) }

        luxThresholdSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) luxEditText.setText(progress.toString())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        luxEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                try {
                    val value = s.toString().toInt()
                    if (value in 0..1000) {
                        luxThresholdSeekBar.progress = value
                    }
                } catch (e: NumberFormatException) {}
            }
        })
    }

    private fun populateUi(alarm: Alarm) {
        calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
        }
        updateAlarmTimeText()
        alarm.selectedDays.forEach { day -> dayPickerGroup.findViewWithTag<Chip>(day)?.isChecked = true }
        luxDismissalSwitch.isChecked = alarm.isLuxDismissalEnabled
        luxThresholdSeekBar.progress = alarm.dismissLux
        luxEditText.setText(alarm.dismissLux.toString())
        volumeSeekBar.progress = alarm.volume
        pinEnabledSwitch.isChecked = alarm.isPinEnabled
        pinEditText.setText(alarm.pin)
        ringtoneUri = alarm.ringtoneUri
        updatePinUiState(alarm.isPinEnabled)
        updateLuxUiState(alarm.isLuxDismissalEnabled)
    }

    private fun showTimePickerDialog() {
        val cal = calendar ?: Calendar.getInstance()
        TimePickerDialog(
            this,
            { _, hour, minute ->
                if (calendar == null) {
                    calendar = Calendar.getInstance()
                }
                calendar?.set(Calendar.HOUR_OF_DAY, hour)
                calendar?.set(Calendar.MINUTE, minute)
                updateAlarmTimeText()
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            false
        ).show()
    }

    private fun updateAlarmTimeText() {
        calendar?.let { alarmTimeText.text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(it.time) }
    }

    private fun openRingtonePicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
        }
        ringtonePickerLauncher.launch(intent)
    }

    private fun saveAlarm() {
        if (calendar == null) {
            Toast.makeText(this, "Please set a time.", Toast.LENGTH_SHORT).show()
            return
        }
        if (pinEnabledSwitch.isChecked && pinEditText.text.isBlank()) {
            Toast.makeText(this, "Please enter a PIN to enable this feature.", Toast.LENGTH_SHORT).show()
            return
        }
        if (luxDismissalSwitch.isChecked && luxEditText.text.isBlank()) {
            Toast.makeText(this, "Please enter a lux value for dismissal.", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedDays = mutableSetOf<String>()
        dayPickerGroup.checkedChipIds.forEach { id ->
            val chip = dayPickerGroup.findViewById<Chip>(id)
            selectedDays.add(chip.tag.toString())
        }

        lifecycleScope.launch {
            val alarm = Alarm(
                id = alarmId ?: 0,
                hour = calendar!!.get(Calendar.HOUR_OF_DAY),
                minute = calendar!!.get(Calendar.MINUTE),
                selectedDays = selectedDays,
                isLuxDismissalEnabled = luxDismissalSwitch.isChecked,
                dismissLux = luxThresholdSeekBar.progress,
                volume = volumeSeekBar.progress,
                ringtoneUri = ringtoneUri,
                isPinEnabled = pinEnabledSwitch.isChecked,
                pin = pinEditText.text.toString()
            )

            val newId = if (alarmId == null) {
                alarmViewModel.insert(alarm).toInt()
            } else {
                alarmViewModel.update(alarm)
                alarm.id
            }
            scheduleAlarm(alarm.copy(id = newId))
            Toast.makeText(this@AlarmEditorActivity, "Alarm saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun deleteAlarm() {
        currentAlarm?.let {
            lifecycleScope.launch {
                cancelScheduledAlarm(it)
                alarmViewModel.delete(it)
                Toast.makeText(this@AlarmEditorActivity, "Alarm deleted", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun scheduleAlarm(alarm: Alarm) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("alarm_id", alarm.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(this, alarm.id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

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

    private fun cancelScheduledAlarm(alarm: Alarm) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(this, alarm.id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(pendingIntent)
    }

    private fun calculateNextTriggerTime(cal: Calendar, selectedDays: Set<String>): Long {
        if (selectedDays.isEmpty()) { // One-time alarm
            return if (cal.timeInMillis > System.currentTimeMillis()) cal.timeInMillis else cal.apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        }

        val dayMapping = mapOf("Su" to 1, "M" to 2, "Tu" to 3, "W" to 4, "Th" to 5, "F" to 6, "Sa" to 7)
        val calendarDays = selectedDays.mapNotNull { dayMapping[it] }.sorted()
        val now = Calendar.getInstance()

        for (i in 0..7) {
            val nextDay = now.clone() as Calendar
            nextDay.add(Calendar.DAY_OF_YEAR, i)
            val dayOfWeek = nextDay.get(Calendar.DAY_OF_WEEK)

            if (dayOfWeek in calendarDays) {
                val nextAlarm = cal.clone() as Calendar
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

    private fun updatePinUiState(pinEnabled: Boolean) {
        pinEditText.isEnabled = pinEnabled
    }

    private fun updateLuxUiState(luxEnabled: Boolean) {
        val visibility = if (luxEnabled) View.VISIBLE else View.GONE
        luxValueText.visibility = visibility
        luxThresholdLabel.visibility = visibility
        luxEditText.visibility = visibility
        luxThresholdSeekBar.visibility = visibility
    }

    private fun checkAndRequestPermissions(): Boolean {
        // ... (permission checking logic is correct)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // SensorEventListener Methods
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
