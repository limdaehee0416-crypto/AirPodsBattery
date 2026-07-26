package com.example.airpodsbattery

/**
 * 에어팟/비츠 이어폰은 케이스를 열거나 착용 중일 때 배터리 정보를
 * 블루투스 저전력(BLE) 광고 신호(Apple manufacturer data, 제조사 ID 76)에
 * 실어서 주변에 방송합니다. 아이폰이 배터리 팝업을 띄울 때 읽는 것이
 * 바로 이 신호이며, 이 클래스는 커뮤니티에 알려진 해당 신호 포맷을
 * 같은 방식으로 해석합니다.
 *
 * 이 포맷은 애플이 공식 문서화한 것이 아니라 여러 오픈소스 프로젝트가
 * 역분석하여 밝혀낸 내용이라, 기기·펌웨어에 따라 값이 살짝 다를 수
 * 있습니다. 오프셋을 모두 상수/변수로 분리해두었으니, 실제 값이
 * 어긋나면 쉽게 조정할 수 있습니다.
 */
object AirPodsParser {

    private val MODEL_MAP = mapOf(
        "0220" to "AirPods Pro",
        "0a20" to "AirPods (1세대)",
        "0e20" to "AirPods (2세대)",
        "0f20" to "AirPods (3세대)",
        "1320" to "AirPods Pro 2",
        "1420" to "AirPods Max",
        "0320" to "Powerbeats3",
        "0520" to "BeatsX",
        "0620" to "Beats Solo3",
        "0920" to "Powerbeats Pro",
        "1020" to "Beats Flex",
        "1120" to "Beats Solo Pro",
        "1720" to "Beats Studio Buds",
        "1a20" to "Beats Fit Pro"
    )

    /**
     * manufacturerData 는 ScanRecord.getManufacturerSpecificData(76) 의
     * 반환값으로, 애플 제조사 ID(2바이트)는 이미 제외된 상태입니다.
     * 정상적인 에어팟 신호는 0x07(Proximity Pairing 타입)로 시작합니다.
     */
    fun parse(manufacturerData: ByteArray): AirPodsStatus? {
        if (manufacturerData.size < 10) return null
        val hex = manufacturerData.joinToString("") { "%02x".format(it) }
        if (hex.length < 20 || !hex.startsWith("07")) return null

        // byte 4-5: 기기 모델 ID
        val modelId = hex.substring(8, 12)
        val model = MODEL_MAP[modelId] ?: "AirPods"

        // byte 7: 상위 니블 / 하위 니블 = 양쪽 이어폰 배터리(0~10)
        val batteryByte = hex.substring(14, 16)
        val nibbleA = batteryByte.substring(0, 1).toInt(16)
        val nibbleB = batteryByte.substring(1, 2).toInt(16)

        // byte 8: 상위 니블 = 충전 상태 플래그, 하위 니블 = 케이스 배터리(0~10)
        val byte8 = hex.substring(16, 18)
        val chargeFlags = byte8.substring(0, 1).toInt(16)
        val caseRaw = byte8.substring(1, 2).toInt(16)

        // byte 9: 좌/우가 뒤바뀌었는지 알려주는 플립 비트
        val byte9 = hex.substring(18, 20).toInt(16)
        val isFlipped = (byte9 and 0x02) == 0

        val leftRaw = if (isFlipped) nibbleB else nibbleA
        val rightRaw = if (isFlipped) nibbleA else nibbleB
        val leftCharging = if (isFlipped) (chargeFlags and 0x02) != 0 else (chargeFlags and 0x01) != 0
        val rightCharging = if (isFlipped) (chargeFlags and 0x01) != 0 else (chargeFlags and 0x02) != 0
        val caseCharging = (chargeFlags and 0x04) != 0

        fun pct(raw: Int) = if (raw in 0..10) raw * 10 else -1

        return AirPodsStatus(
            model = model,
            leftBattery = pct(leftRaw),
            rightBattery = pct(rightRaw),
            caseBattery = pct(caseRaw),
            leftCharging = leftCharging,
            rightCharging = rightCharging,
            caseCharging = caseCharging,
            timestamp = System.currentTimeMillis()
        )
    }
}
