package com.example.airpodsbattery

/**
 * 에어팟 한 개 신호에서 해석해낸 상태 값.
 * battery 값은 0~100(10% 단위) 이며, 값을 알 수 없으면 -1 입니다.
 */
data class AirPodsStatus(
    val model: String,
    val leftBattery: Int,
    val rightBattery: Int,
    val caseBattery: Int,
    val leftCharging: Boolean,
    val rightCharging: Boolean,
    val caseCharging: Boolean,
    val timestamp: Long
)
