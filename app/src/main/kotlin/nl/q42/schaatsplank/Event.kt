package nl.q42.schaatsplank

import android.hardware.SensorEvent

data class Event(val timestamp: Long = 0, val values: FloatArray = FloatArray(6)) {
    constructor(sensorEvent: SensorEvent?) : this(sensorEvent?.timestamp ?: 0, sensorEvent?.values ?: FloatArray(0))
}