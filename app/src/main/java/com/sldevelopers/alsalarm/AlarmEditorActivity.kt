package com.sldevelopers.alsalarm

import android.app.AlarmManager
import android.app.AlertDialog
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
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.TimePicker
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.sldevelopers.alsalarm.data.Alarm
import com.sldevelopers.alsalarm.data.AlarmDatabase
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

class AlarmEditorActivity : AppCompatActivity(), SensorEventListener {

    private var alarmId: Int? = null
    private var ringtoneUri: String? = null
    private var currentAlarm: Alarm? = null

    private lateinit var alarmViewModel: AlarmViewModel
    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private lateinit var vibrator: Vibrator

    // Views
    private lateinit var alarmTimePicker: TimePicker
    private lateinit var dayPickerGroup: ChipGroup
    private lateinit var saveAlarmButton: Button
    private lateinit var deleteAlarmButton: Button
    private lateinit var previewAlarmButton: Button
    private lateinit var luxDismissalSwitch: SwitchMaterial
    private lateinit var luxValueText: TextView
    private lateinit var luxThresholdLabel: TextView
    private lateinit var luxEditText: EditText
    private lateinit var luxThresholdSeekBar: SeekBar
    private lateinit var volumeSeekBar: SeekBar
    private lateinit var ringtoneButton: Button
    private lateinit var pinEnabledSwitch: SwitchMaterial
    private lateinit var pinEditText: EditText
    private lateinit var alarmLabelEditText: TextInputEditText

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

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

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
        alarmTimePicker = findViewById(R.id.alarmTimePicker)
        dayPickerGroup = findViewById(R.id.dayPickerGroup)
        saveAlarmButton = findViewById(R.id.saveAlarmButton)
        deleteAlarmButton = findViewById(R.id.deleteAlarmButton)
        previewAlarmButton = findViewById(R.id.previewAlarmButton)
        luxDismissalSwitch = findViewById(R.id.luxDismissalSwitch)
        luxValueText = findViewById(R.id.luxValueText)
        luxThresholdLabel = findViewById(R.id.luxThresholdLabel)
        luxEditText = findViewById(R.id.luxEditText)
        luxThresholdSeekBar = findViewById(R.id.luxThresholdSeekBar)
        volumeSeekBar = findViewById(R.id.volumeSeekBar)
        ringtoneButton = findViewById(R.id.ringtoneButton)
        pinEnabledSwitch = findViewById(R.id.pinEnabledSwitch)
        pinEditText = findViewById(R.id.pinEditText)
        alarmLabelEditText = findViewById(R.id.alarmLabelEditText)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    }

    private fun initializeViewModel() {
        val alarmDao = AlarmDatabase.getDatabase(application).alarmDao()
        val viewModelFactory = AlarmViewModelFactory(alarmDao)
        alarmViewModel = ViewModelProvider(this, viewModelFactory)[AlarmViewModel::class.java]
    }

    private fun setListeners() {
        saveAlarmButton.setOnClickListener { 
            vibrate(VibrationEffect.EFFECT_HEAVY_CLICK)
            if (checkAndRequestPermissions()) saveAlarm() 
        }
        deleteAlarmButton.setOnClickListener { 
            vibrate(VibrationEffect.EFFECT_HEAVY_CLICK)
            deleteAlarm() 
        }
        previewAlarmButton.setOnClickListener { 
            vibrate(VibrationEffect.EFFECT_CLICK)
            previewAlarm() 
        }
        ringtoneButton.setOnClickListener { 
            vibrate(VibrationEffect.EFFECT_CLICK)
            openRingtonePicker() 
        }
        pinEnabledSwitch.setOnCheckedChangeListener { _, isChecked -> 
            vibrate(VibrationEffect.EFFECT_TICK)
            updatePinUiState(isChecked) 
        }
        luxDismissalSwitch.setOnCheckedChangeListener { _, isChecked -> 
            vibrate(VibrationEffect.EFFECT_TICK)
            updateLuxUiState(isChecked) 
        }

        alarmTimePicker.setOnTimeChangedListener { _, _, _ ->
            vibrate(VibrationEffect.EFFECT_TICK)
        }

        for (i in 0 until dayPickerGroup.childCount) {
            val chip = dayPickerGroup.getChildAt(i) as Chip
            chip.setOnClickListener {
                vibrate(VibrationEffect.EFFECT_CLICK)
            }
        }

        findViewById<ImageButton>(R.id.lux_editor_info_button).setOnClickListener {
            vibrate(VibrationEffect.EFFECT_CLICK)
            showInfoDialog("LUX Dismissal", "The alarm will only dismiss when the room\'s brightness (LUX) reaches the required level.")
        }

        findViewById<ImageButton>(R.id.pin_editor_info_button).setOnClickListener {
            vibrate(VibrationEffect.EFFECT_CLICK)
            showInfoDialog("PIN Dismissal", "You must enter the correct PIN to dismiss the alarm.")
        }

        findViewById<Button>(R.id.add5minButton).setOnClickListener { addMinutes(5) }
        findViewById<Button>(R.id.add10minButton).setOnClickListener { addMinutes(10) }
        findViewById<Button>(R.id.add20minButton).setOnClickListener { addMinutes(20) }
        findViewById<Button>(R.id.add40minButton).setOnClickListener { addMinutes(40) }

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

    private fun addMinutes(minutes: Int) {
        vibrate(VibrationEffect.EFFECT_TICK)
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, alarmTimePicker.hour)
        calendar.set(Calendar.MINUTE, alarmTimePicker.minute)
        calendar.add(Calendar.MINUTE, minutes)
        alarmTimePicker.hour = calendar.get(Calendar.HOUR_OF_DAY)
        alarmTimePicker.minute = calendar.get(Calendar.MINUTE)
    }

    private fun populateUi(alarm: Alarm) {
        alarmTimePicker.hour = alarm.hour
        alarmTimePicker.minute = alarm.minute
        alarmLabelEditText.setText(alarm.label)
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

    private fun openRingtonePicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
        }
        ringtonePickerLauncher.launch(intent)
    }

    private fun saveAlarm() {
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
                hour = alarmTimePicker.hour,
                minute = alarmTimePicker.minute,
                label = alarmLabelEditText.text.toString(),
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
            
            val finalAlarm = alarm.copy(id = newId)
            AlarmScheduler.schedule(this@AlarmEditorActivity, finalAlarm)
            
            val triggerTime = AlarmScheduler.calculateNextTriggerTime(finalAlarm)
            val message = if (triggerTime != -1L) {
                val now = System.currentTimeMillis()
                val diff = triggerTime - now
                val days = TimeUnit.MILLISECONDS.toDays(diff)
                val hours = TimeUnit.MILLISECONDS.toHours(diff) % 24
                val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60

                val dayString = if (days > 0) "$days day(s), " else ""
                "Alarm set for $dayString$hours hour(s) and $minutes minute(s) from now."
            } else {
                "Alarm saved"
            }
            Toast.makeText(this@AlarmEditorActivity, message, Toast.LENGTH_LONG).show()
            
            finish()
        }
    }

    private fun deleteAlarm() {
        currentAlarm?.let {
            lifecycleScope.launch {
                AlarmScheduler.cancel(this@AlarmEditorActivity, it)
                alarmViewModel.delete(it)
                Toast.makeText(this@AlarmEditorActivity, "Alarm deleted", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun previewAlarm() {
        if (alarmId == null) {
            // It's a new alarm, construct a temporary alarm object and pass its details
            val intent = Intent(this, AlarmScreenActivity::class.java).apply {
                putExtra("is_preview", true)
                putExtra("alarm_id", -1) // Indicate it's a temporary alarm for preview
                putExtra("hour", alarmTimePicker.hour)
                putExtra("minute", alarmTimePicker.minute)
                putExtra("label", alarmLabelEditText.text.toString())

                val selectedDays = mutableListOf<String>()
                dayPickerGroup.checkedChipIds.forEach { id ->
                    val chip = dayPickerGroup.findViewById<Chip>(id)
                    selectedDays.add(chip.tag.toString())
                }
                putStringArrayListExtra("selected_days", ArrayList(selectedDays))

                putExtra("lux_enabled", luxDismissalSwitch.isChecked)
                putExtra("lux_dismiss_level", luxThresholdSeekBar.progress)
                putExtra("volume", volumeSeekBar.progress)
                putExtra("ringtone_uri", ringtoneUri)
                putExtra("pin_enabled", pinEnabledSwitch.isChecked)
                putExtra("pin", pinEditText.text.toString())
            }
            startActivity(intent)
        } else {
            // It's an existing alarm, use the existing ID
            launchAlarmScreen(true)
        }
    }

    private fun launchAlarmScreen(isPreview: Boolean) {
        val intent = Intent(this, AlarmScreenActivity::class.java).apply {
            val id = alarmId ?: -1
            putExtra("alarm_id", id)
            putExtra("is_preview", isPreview)
        }
        startActivity(intent)
    }

    private fun showInfoDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
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
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Intent().also { intent ->
                intent.action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                startActivity(intent)
            }
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("This app needs permission to appear on top to display the alarm screen. Please grant this permission in the app settings.")
                .setPositiveButton("Go to Settings") { _, _ ->
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
            return false
        }
        return true
    }

    private fun vibrate(effect: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(effect))
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        if (hasUnsavedChanges()) {
            showUnsavedChangesDialog()
        } else {
            super.onBackPressed()
        }
    }

    private fun hasUnsavedChanges(): Boolean {
        val selectedDays = mutableSetOf<String>()
        dayPickerGroup.checkedChipIds.forEach { id ->
            val chip = dayPickerGroup.findViewById<Chip>(id)
            selectedDays.add(chip.tag.toString())
        }

        val newAlarm = Alarm(
            id = alarmId ?: 0,
            hour = alarmTimePicker.hour,
            minute = alarmTimePicker.minute,
            label = alarmLabelEditText.text.toString(),
            selectedDays = selectedDays,
            isLuxDismissalEnabled = luxDismissalSwitch.isChecked,
            dismissLux = luxThresholdSeekBar.progress,
            volume = volumeSeekBar.progress,
            ringtoneUri = ringtoneUri,
            isPinEnabled = pinEnabledSwitch.isChecked,
            pin = pinEditText.text.toString()
        )

        return currentAlarm != newAlarm
    }

    private fun showUnsavedChangesDialog() {
        AlertDialog.Builder(this)
            .setTitle("Unsaved Changes")
            .setMessage("You have unsaved changes. Do you want to save them?")
            .setPositiveButton("Save") { _, _ -> saveAlarm() }
            .setNegativeButton("Discard") { _, _ -> finish() }
            .setNeutralButton("Cancel", null)
            .show()
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
