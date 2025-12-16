package com.sldevelopers.alsalarm

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
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
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                val alarms = adapter.currentList.toMutableList()
                Collections.swap(alarms, fromPosition, toPosition)
                adapter.submitList(alarms)
                updateAlarmOrder(alarms)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

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
            isSelectionMode = { isSelectionMode },
            isSelected = { alarm -> selectedAlarms.contains(alarm) },
            onStartDrag = { viewHolder ->
                if (!isSelectionMode) {
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
            adapter.submitList(alarms)
            if (alarms.isEmpty()) {
                recyclerView.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
            } else {
                recyclerView.visibility = View.VISIBLE
                emptyView.visibility = View.GONE
            }
        }

        deleteButton.setOnClickListener {
            selectedAlarms.forEach { alarmViewModel.delete(it) }
            exitSelectionMode()
        }
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
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add_alarm -> {
                val intent = Intent(this, AlarmEditorActivity::class.java)
                startActivity(intent)
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
