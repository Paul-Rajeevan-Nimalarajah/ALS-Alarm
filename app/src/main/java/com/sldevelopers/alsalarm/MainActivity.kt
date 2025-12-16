package com.sldevelopers.alsalarm

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sldevelopers.alsalarm.data.Alarm
import com.sldevelopers.alsalarm.data.AlarmDatabase
import java.util.Collections

class MainActivity : AppCompatActivity() {

    private lateinit var alarmViewModel: AlarmViewModel
    private lateinit var emptyView: TextView
    private lateinit var adapter: AlarmAdapter
    private lateinit var deleteButtonContainer: LinearLayout
    private lateinit var itemTouchHelper: ItemTouchHelper

    private val selectedAlarms = mutableListOf<Alarm>()
    private var isSelectionMode = false
    private var isSortedByTime = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        deleteButtonContainer = findViewById(R.id.delete_button_container)
        val deleteButton: Button = findViewById(R.id.delete_button)

        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                isSortedByTime = false // Switch to custom order on drag
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                adapter.moveItem(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

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
                    deleteButtonContainer.visibility = View.VISIBLE
                    invalidateOptionsMenu() // Hide the add alarm button
                }
                toggleSelection(alarm)
            },
            onAlarmEnabledToggled = { alarm, isEnabled ->
                alarm.isEnabled = isEnabled
                alarmViewModel.update(alarm)
                if (isEnabled) {
                    AlarmScheduler.schedule(this, alarm)
                } else {
                    AlarmScheduler.cancel(this, alarm)
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

        val recyclerView = findViewById<RecyclerView>(R.id.alarmsRecyclerView)
        emptyView = findViewById(R.id.empty_view)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        itemTouchHelper.attachToRecyclerView(recyclerView)

        val alarmDao = AlarmDatabase.getDatabase(application).alarmDao()
        val viewModelFactory = AlarmViewModelFactory(alarmDao)
        alarmViewModel = ViewModelProvider(this, viewModelFactory).get(AlarmViewModel::class.java)

        alarmViewModel.allAlarms.observe(this) { alarms ->
            val displayAlarms = if (isSortedByTime) {
                alarms.sortedWith(compareBy({ it.hour }, { it.minute }))
            } else {
                alarms.sortedBy { it.displayOrder }
            }
            adapter.setData(displayAlarms)

            if (alarms.isEmpty()) {
                recyclerView.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
                adapter.nextAlarmId = null
            } else {
                recyclerView.visibility = View.VISIBLE
                emptyView.visibility = View.GONE
                updateNextAlarmHighlight(alarms)
            }
        }

        deleteButton.setOnClickListener {
            selectedAlarms.forEach { 
                AlarmScheduler.cancel(this, it)
                alarmViewModel.delete(it) 
            }
            exitSelectionMode()
        }
    }

    private fun updateNextAlarmHighlight(alarms: List<Alarm>) {
        var nextAlarm: Alarm? = null
        var nextAlarmTime = Long.MAX_VALUE

        alarms.forEach { alarm ->
            val triggerTime = AlarmScheduler.calculateNextTriggerTime(alarm)
            if (triggerTime != -1L && triggerTime < nextAlarmTime) {
                nextAlarmTime = triggerTime
                nextAlarm = alarm
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
        deleteButtonContainer.visibility = View.GONE
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
                    adapter.setData(displayAlarms)
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
}
