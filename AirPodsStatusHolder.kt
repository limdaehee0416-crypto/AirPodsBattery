package com.example.airpodsbattery

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 백그라운드 서비스(AirPodsScanService)가 감지한 최신 배터리 상태를
 * 화면(MainActivity)과 공유하기 위한 프로세스 내 저장소.
 */
object AirPodsStatusHolder {
    private val _status = MutableStateFlow<AirPodsStatus?>(null)
    val status: StateFlow<AirPodsStatus?> = _status.asStateFlow()

    fun update(newStatus: AirPodsStatus?) {
        _status.value = newStatus
    }
}
