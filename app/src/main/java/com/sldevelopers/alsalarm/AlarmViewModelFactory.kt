package com.sldevelopers.alsalarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sldevelopers.alsalarm.data.AlarmDao

class AlarmViewModelFactory(private val alarmDao: AlarmDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AlarmViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AlarmViewModel(alarmDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
