package com.locationtracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationtracker.data.location.LocationMode
import com.locationtracker.data.location.LocationProvider
import com.locationtracker.data.repository.LocationRepository
import com.locationtracker.util.TrackSplitter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val locationProvider: LocationProvider,
    private val repository: LocationRepository
) : ViewModel() {

    val isTracking = locationProvider.isActive.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )
    val currentMode = MutableStateFlow(LocationMode.BALANCED)
    val locationText = MutableStateFlow("等待定位...")
    val dates: StateFlow<List<String>> = repository.allDates().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    // 轨迹回放
    val selectedDate = MutableStateFlow(today())
    val segments = MutableStateFlow<List<TrackSplitter.TrackSegment>>(emptyList())
    val distance = MutableStateFlow(0.0)
    val recordCount = MutableStateFlow(0)

    // 权限引导
    val permissionLevel = MutableStateFlow(PermissionLevel.NONE)

    init {
        viewModelScope.launch {
            locationProvider.latest.collect { loc ->
                loc?.let {
                    locationText.value = "%.6f, %.6f (±%.1fm)".format(
                        it.latitude, it.longitude, it.accuracy
                    )
                }
            }
        }
    }

    fun switchMode(m: LocationMode) {
        currentMode.value = m
        locationProvider.switchMode(m)
    }

    fun selectDate(date: String) {
        selectedDate.value = date
        viewModelScope.launch {
            segments.value = repository.segmentsByDate(date)
            distance.value = repository.distanceByDate(date)
            recordCount.value = repository.recordsByDate(date).size
        }
    }

    fun deleteDate(date: String) {
        viewModelScope.launch {
            repository.deleteDate(date)
            selectDate(today())
        }
    }

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())

    enum class PermissionLevel { NONE, FOREGROUND_ONLY, FULL }
}
