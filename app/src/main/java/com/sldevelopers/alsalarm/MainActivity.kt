package com.sldevelopers.alsalarm

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.sldevelopers.alsalarm.data.Alarm
import com.sldevelopers.alsalarm.data.AlarmDatabase
import com.sldevelopers.alsalarm.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var alarmViewModel: AlarmViewModel
    private lateinit var adapter: AlarmAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    private val selectedAlarms = mutableListOf<Alarm>()
    private var isSelectionMode = false
    private var isSortedByTime = false
    private var isFirstLoad = true

    private val sharedPrefs by lazy { getSharedPreferences("AlarmSkippedState", Context.MODE_PRIVATE) }
    private val skippedAlarms = mutableMapOf<Int, Long>()

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadSkippedAlarms()

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setOnClickListener {
            vibrate()
            val url = "https://github.com/Paul-Rajeevan-Nimalarajah"
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            startActivity(intent)
        }

        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                isSortedByTime = false // Switch to custom order on drag
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                adapter.moveItem(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val alarm = adapter.getAlarms()[position]
                alarmViewModel.delete(alarm)
                Snackbar.make(binding.root, "Alarm deleted", Snackbar.LENGTH_LONG)
                    .setAction("Undo") { 
                        lifecycleScope.launch {
                            alarmViewModel.insert(alarm) 
                        }
                    }
                    .show()
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                updateAlarmOrder(adapter.getAlarms())
            }

            override fun isLongPressDragEnabled(): Boolean {
                return false // We will start drag on touch of the handle
            }
        }

        itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)

        adapter = AlarmAdapter(
            onAlarmClicked = { alarm ->
                if (isSelectionMode) {
                    toggleSelection(alarm)
                } else {
                    val intent = Intent(this, AlarmEditorActivity::class.java)
                    intent.putExtra("alarm_id", alarm.id)
                    startActivity(intent)
                }
            },
            onAlarmLongClicked = { alarm ->
                if (!isSelectionMode) {
                    isSelectionMode = true
                    binding.deleteButtonContainer.visibility = View.VISIBLE
                    invalidateOptionsMenu() // Hide the add alarm button
                }
                toggleSelection(alarm)
            },
            onAlarmEnabledToggled = { alarm, isEnabled ->
                if (!isEnabled && alarm.selectedDays.isNotEmpty()) {
                    AlertDialog.Builder(this)
                        .setTitle("Disable Repeating Alarm")
                        .setMessage("Do you want to skip the next alarm or disable it permanently?")
                        .setPositiveButton("Disable") { _, _ ->
                            vibrate()
                            alarm.isEnabled = false
                            skippedAlarms.remove(alarm.id)
                            removeSkippedAlarm(alarm.id)
                            alarmViewModel.update(alarm)
                            AlarmScheduler.cancel(this, alarm)
                        }
                        .setNegativeButton("Skip Once") { _, _ ->
                            val skippedUntil = AlarmScheduler.skipNext(this, alarm)
                            skippedAlarms[alarm.id] = skippedUntil
                            saveSkippedAlarm(alarm.id, skippedUntil)
                            alarm.skippedUntil = skippedUntil
                            adapter.notifyItemChanged(adapter.getAlarms().indexOf(alarm))
                            updateNextAlarmHighlight(adapter.getAlarms())
                        }
                        .setNeutralButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                            adapter.notifyItemChanged(adapter.getAlarms().indexOf(alarm))
                        }
                        .setOnCancelListener {
                            adapter.notifyItemChanged(adapter.getAlarms().indexOf(alarm))
                        }
                        .show()
                } else {
                    vibrate()
                    alarm.isEnabled = isEnabled
                    skippedAlarms.remove(alarm.id)
                    removeSkippedAlarm(alarm.id)
                    alarm.skippedUntil = 0
                    alarmViewModel.update(alarm)
                    if (isEnabled) {
                        AlarmScheduler.schedule(this, alarm)
                    } else {
                        AlarmScheduler.cancel(this, alarm)
                    }
                    updateNextAlarmHighlight(adapter.getAlarms())
                }
            },
            isSelectionMode = { isSelectionMode },
            isSelected = { alarm -> selectedAlarms.contains(alarm) },
            onStartDrag = { viewHolder ->
                if (!isSelectionMode) {
                    isSortedByTime = false // Switch to custom order on drag
                    itemTouchHelper.startDrag(viewHolder)
                }
            }
        )

        binding.alarmsRecyclerView.adapter = adapter
        binding.alarmsRecyclerView.layoutManager = LinearLayoutManager(this)
        itemTouchHelper.attachToRecyclerView(binding.alarmsRecyclerView)

        val alarmDao = AlarmDatabase.getDatabase(application).alarmDao()
        val viewModelFactory = AlarmViewModelFactory(alarmDao)
        alarmViewModel = ViewModelProvider(this, viewModelFactory).get(AlarmViewModel::class.java)

        alarmViewModel.allAlarms.observe(this) { alarms ->
            alarms.forEach { alarm ->
                val skippedTime = skippedAlarms[alarm.id]
                if (skippedTime != null && skippedTime > System.currentTimeMillis()) {
                    alarm.skippedUntil = skippedTime
                } else {
                    if (skippedAlarms.containsKey(alarm.id)) {
                        skippedAlarms.remove(alarm.id)
                        removeSkippedAlarm(alarm.id)
                    }
                }
            }

            val displayAlarms = if (isSortedByTime) {
                alarms.sortedWith(compareBy({ it.hour }, { it.minute }))
            } else {
                alarms.sortedBy { it.displayOrder }
            }
            adapter.submitList(displayAlarms)

            if (alarms.isEmpty()) {
                binding.alarmsRecyclerView.visibility = View.GONE
                binding.emptyView.visibility = View.VISIBLE
                adapter.nextAlarmId = null
            } else {
                binding.alarmsRecyclerView.visibility = View.VISIBLE
                binding.emptyView.visibility = View.GONE
                updateNextAlarmHighlight(alarms)
            }

            if (isFirstLoad) {
                showNextAlarmToast(alarms)
                isFirstLoad = false
            }
        }

        binding.deleteButton.setOnClickListener {
            vibrate()
            selectedAlarms.forEach { 
                AlarmScheduler.cancel(this, it)
                alarmViewModel.delete(it) 
            }
            exitSelectionMode()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh adapter data on resume to re-evaluate skipped state and show toast
        alarmViewModel.allAlarms.value?.let { alarms ->
            adapter.submitList(alarms)
            updateNextAlarmHighlight(alarms)
            if (!isFirstLoad) {
                showNextAlarmToast(alarms)
            }
        }
    }

    private fun saveSkippedAlarm(alarmId: Int, skippedUntil: Long) {
        with(sharedPrefs.edit()) {
            putLong(alarmId.toString(), skippedUntil)
            apply()
        }
    }

    private fun removeSkippedAlarm(alarmId: Int) {
        with(sharedPrefs.edit()) {
            remove(alarmId.toString())
            apply()
        }
    }

    private fun loadSkippedAlarms() {
        sharedPrefs.all.forEach { (key, value) ->
            try {
                val alarmId = key.toInt()
                val skippedUntil = value as Long
                if (skippedUntil > System.currentTimeMillis()) {
                    skippedAlarms[alarmId] = skippedUntil
                } else {
                    removeSkippedAlarm(alarmId)
                }
            } catch (e: Exception) {
                // Could be a parsing error, clean up the pref
                with(sharedPrefs.edit()) {
                    remove(key)
                    apply()
                }
            }
        }
    }

    private fun showNextAlarmToast(alarms: List<Alarm>) {
        var nextAlarm: Alarm? = null
        var nextAlarmTime = Long.MAX_VALUE

        alarms.forEach { alarm ->
            if (alarm.isEnabled) {
                val triggerTime = AlarmScheduler.calculateNextTriggerTime(alarm)
                if (triggerTime != -1L && triggerTime < nextAlarmTime) {
                    nextAlarmTime = triggerTime
                    nextAlarm = alarm
                }
            }
        }

        if (nextAlarm != null) {
            val now = System.currentTimeMillis()
            val diff = nextAlarmTime - now
            if (diff > 0) {
                val days = TimeUnit.MILLISECONDS.toDays(diff)
                val hours = TimeUnit.MILLISECONDS.toHours(diff) % 24
                val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
                val dayString = if (days > 0) "$days day(s), " else ""
                Toast.makeText(this, "Next alarm in $dayString$hours hour(s) and $minutes minute(s)", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateNextAlarmHighlight(alarms: List<Alarm>) {
        var nextAlarm: Alarm? = null
        var nextAlarmTime = Long.MAX_VALUE

        alarms.forEach { alarm ->
            val isSkipped = skippedAlarms[alarm.id]?.let { it > System.currentTimeMillis() } ?: false
            if (alarm.isEnabled && !isSkipped) {
                val triggerTime = AlarmScheduler.calculateNextTriggerTime(alarm)
                if (triggerTime != -1L && triggerTime < nextAlarmTime) {
                    nextAlarmTime = triggerTime
                    nextAlarm = alarm
                }
            }
        }
        adapter.nextAlarmId = nextAlarm?.id
        adapter.notifyDataSetChanged()
    }

    private fun updateAlarmOrder(alarms: List<Alarm>) {
        for ((index, alarm) in alarms.withIndex()) {
            alarm.displayOrder = index
        }
        alarmViewModel.updateAlarms(alarms)
    }

    private fun toggleSelection(alarm: Alarm) {
        if (selectedAlarms.contains(alarm)) {
            selectedAlarms.remove(alarm)
        } else {
            selectedAlarms.add(alarm)
        }
        if (selectedAlarms.isEmpty()) {
            exitSelectionMode()
        }
        adapter.notifyDataSetChanged()
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        selectedAlarms.clear()
        binding.deleteButtonContainer.visibility = View.GONE
        invalidateOptionsMenu() // Show the add alarm button again
        adapter.notifyDataSetChanged()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.findItem(R.id.action_add_alarm)?.isVisible = !isSelectionMode
        menu?.findItem(R.id.action_sort)?.isVisible = !isSelectionMode
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        vibrate()
        return when (item.itemId) {
            R.id.action_add_alarm -> {
                val intent = Intent(this, AlarmEditorActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.action_sort -> {
                isSortedByTime = !isSortedByTime
                alarmViewModel.allAlarms.value?.let { alarms ->
                    val displayAlarms = if (isSortedByTime) {
                        alarms.sortedWith(compareBy({ it.hour }, { it.minute }))
                    } else {
                        alarms.sortedBy { it.displayOrder }
                    }
                    adapter.submitList(displayAlarms)
                    updateNextAlarmHighlight(displayAlarms)
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        if(isSelectionMode) {
            exitSelectionMode()
        } else {
            super.onBackPressed()
        }
    }

    private fun vibrate() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            //deprecated in API 26 
            vibrator.vibrate(50)
        }
    }
}
