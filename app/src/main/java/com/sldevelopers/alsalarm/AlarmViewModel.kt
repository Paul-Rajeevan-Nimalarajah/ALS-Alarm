package com.sldevelopers.alsalarm

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.sldevelopers.alsalarm.data.Alarm
import com.sldevelopers.alsalarm.data.AlarmDao
import kotlinx.coroutines.launch

class AlarmViewModel(private val alarmDao: AlarmDao) : ViewModel() {

    val allAlarms: LiveData<List<Alarm>> = alarmDao.getAllAlarms()

    fun getAlarm(id: Int): LiveData<Alarm?> {
        return alarmDao.getAlarm(id).asLiveData()
    }

    suspend fun insert(alarm: Alarm): Long {
        return alarmDao.insert(alarm)
    }

    fun update(alarm: Alarm) = viewModelScope.launch {
        alarmDao.update(alarm)
    }

    fun delete(alarm: Alarm) = viewModelScope.launch {
        alarmDao.delete(alarm)
    }
}
