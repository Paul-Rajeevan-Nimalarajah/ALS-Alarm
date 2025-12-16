package com.sldevelopers.alsalarm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.sldevelopers.alsalarm.data.Alarm
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AlarmAdapter(private val onAlarmClicked: (Alarm) -> Unit) :
    ListAdapter<Alarm, AlarmAdapter.AlarmViewHolder>(AlarmsComparator()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        return AlarmViewHolder.create(parent)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        val current = getItem(position)
        holder.bind(current)
        holder.itemView.setOnClickListener { onAlarmClicked(current) }
    }

    class AlarmViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val alarmTime: TextView = itemView.findViewById(R.id.alarm_time)
        private val alarmDays: TextView = itemView.findViewById(R.id.alarm_days)
        private val alarmEnabledSwitch: SwitchMaterial = itemView.findViewById(R.id.alarm_enabled_switch)

        fun bind(alarm: Alarm) {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
            }
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            alarmTime.text = sdf.format(calendar.time)
            alarmDays.text = if(alarm.selectedDays.isEmpty()) "One-time" else alarm.selectedDays.joinToString(", ")
            alarmEnabledSwitch.isChecked = alarm.isEnabled
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
