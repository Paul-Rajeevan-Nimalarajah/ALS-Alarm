package com.sldevelopers.alsalarm

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.sldevelopers.alsalarm.data.Alarm
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AlarmAdapter(
    private val onAlarmClicked: (Alarm) -> Unit,
    private val onAlarmLongClicked: (Alarm) -> Unit,
    private val isSelectionMode: () -> Boolean,
    private val isSelected: (Alarm) -> Boolean,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : ListAdapter<Alarm, AlarmAdapter.AlarmViewHolder>(AlarmsComparator()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        return AlarmViewHolder.create(parent)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        val current = getItem(position)
        holder.bind(current, isSelected(current))
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
            false
        }
    }

    class AlarmViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val alarmTime: TextView = itemView.findViewById(R.id.alarm_time)
        private val alarmDays: TextView = itemView.findViewById(R.id.alarm_days)
        private val alarmEnabledSwitch: SwitchMaterial = itemView.findViewById(R.id.alarm_enabled_switch)
        val dragHandle: ImageView = itemView.findViewById(R.id.drag_handle)

        fun bind(alarm: Alarm, isSelected: Boolean) {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
            }
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            alarmTime.text = sdf.format(calendar.time)
            alarmDays.text = if (alarm.selectedDays.isEmpty()) "One-time" else alarm.selectedDays.joinToString(", ")
            alarmEnabledSwitch.isChecked = alarm.isEnabled

            itemView.setBackgroundColor(if (isSelected) Color.LTGRAY else Color.TRANSPARENT)
        }

        companion object {
            fun create(parent: ViewGroup): AlarmViewHolder {
                val view: View = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_alarm, parent, false)
                return AlarmViewHolder(view)
            }
        }
    }

    class AlarmsComparator : DiffUtil.ItemCallback<Alarm>() {
        override fun areItemsTheSame(oldItem: Alarm, newItem: Alarm): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Alarm, newItem: Alarm): Boolean {
            return oldItem == newItem
        }
    }
}
