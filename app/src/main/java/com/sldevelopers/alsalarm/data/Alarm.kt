package com.sldevelopers.alsalarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val hour: Int,
    val minute: Int,
    var isEnabled: Boolean = true,
    val selectedDays: Set<String> = emptySet(),
    val label: String? = null,
    val isLuxDismissalEnabled: Boolean = true,
    val dismissLux: Int = 50,
    val volume: Int = 80,
    val ringtoneUri: String? = null,
    val isPinEnabled: Boolean = false,
    val pin: String? = null,
    var displayOrder: Int = 0
)
