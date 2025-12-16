package com.sldevelopers.alsalarm

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.sldevelopers.alsalarm.data.Alarm
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Collections
import java.util.Locale

class AlarmAdapter(
    private val onAlarmClicked: (Alarm) -> Unit,
    private val onAlarmLongClicked: (Alarm) -> Unit,
    private val onAlarmEnabledToggled: (Alarm, Boolean) -> Unit,
    private val isSelectionMode: () -> Boolean,
    private val isSelected: (Alarm) -> Boolean,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder>() {

    private val alarms = mutableListOf<Alarm>()
    var nextAlarmId: Int? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val holder = AlarmViewHolder.create(parent)
        holder.alarmEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            val position = holder.adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                val alarm = alarms[position]
                if (alarm.isEnabled != isChecked) { // Prevent infinite loops
                    onAlarmEnabledToggled(alarm, isChecked)
                }
            }
        }
        return holder
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        val current = alarms[position]
        holder.bind(current, isSelected(current), current.id == nextAlarmId)
        holder.itemView.setOnClickListener {
            if (isSelectionMode()) {
                onAlarmLongClicked(current)
            } else {
                onAlarmClicked(current)
            }
        }
        holder.itemView.setOnLongClickListener {
            onAlarmLongClicked(current)
            true
        }
        holder.dragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                onStartDrag(holder)
            }
            true // Consume the touch event
        }
    }

    override fun getItemCount(): Int = alarms.size

    @SuppressLint("NotifyDataSetChanged")
    fun setData(newAlarms: List<Alarm>) {
        this.alarms.clear()
        this.alarms.addAll(newAlarms)
        notifyDataSetChanged()
    }

    fun getAlarms(): List<Alarm> {
        return alarms
    }

    fun moveItem(from: Int, to: Int) {
        Collections.swap(alarms, from, to)
        notifyItemMoved(from, to)
    }

    class AlarmViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val alarmTime: TextView = itemView.findViewById(R.id.alarm_time)
        private val alarmLabel: TextView = itemView.findViewById(R.id.alarm_label)
        private val alarmDays: TextView = itemView.findViewById(R.id.alarm_days)
        private val nextAlarmLabel: TextView = itemView.findViewById(R.id.next_alarm_label)
        val alarmEnabledSwitch: SwitchMaterial = itemView.findViewById(R.id.alarm_enabled_switch)
        val dragHandle: ImageView = itemView.findViewById(R.id.drag_handle)

        fun bind(alarm: Alarm, isSelected: Boolean, isNext: Boolean) {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
            }
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            alarmTime.text = sdf.format(calendar.time)

            if (alarm.label.isNullOrBlank()) {
                alarmLabel.visibility = View.GONE
            } else {
                alarmLabel.visibility = View.VISIBLE
                alarmLabel.text = alarm.label
            }

            alarmDays.text = if (alarm.selectedDays.isEmpty()) "One-time" else alarm.selectedDays.joinToString(", ")
            alarmEnabledSwitch.isChecked = alarm.isEnabled
            nextAlarmLabel.visibility = if (isNext) View.VISIBLE else View.GONE

            itemView.setBackgroundColor(if (isSelected) ContextCompat.getColor(itemView.context, R.color.selected_alarm_highlight) else ContextCompat.getColor(itemView.context, android.R.color.transparent))
        }

        companion object {
            fun create(parent: ViewGroup): AlarmViewHolder {
                val view: View = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_alarm, parent, false)
                return AlarmViewHolder(view)
            }
        }
    }
}
