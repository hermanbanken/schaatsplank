package nl.q42.schaatsplank

import com.google.gson.JsonObject

/**
 * @author Herman Banken, Q42
 */
data class Frequency(val f: Float, val time: Float? = 0f)
data class Gravity(val z: Float, val y: Float) {
    val factor = Algorithm.gravityFunction(z)
    val tilt = Math.atan2(z.toDouble(), y.toDouble())
    val tiltAngle = tilt * 180 / Math.PI
    companion object {
        fun fromAngle(a: Float): Gravity {
            val rd = a / 180 * Math.PI
            return Gravity(Math.sin(rd).toFloat(), Math.cos(rd).toFloat())
        }
    }

    fun  toJson(): JsonObject {
        val o = JsonObject()
        o.addProperty("factor", factor)
        o.addProperty("angle", tiltAngle)
        return o
    }
}
data class Stability(val factor: Float)

data class State(val speed: Float, val distance: Float, val time: Long, val relTime: Long = 0, val where: Where = Where.MIDWAY) {
    fun then(acc: Event): State {
        if(time == 0L) {
            return State(speed, distance, acc.timestamp, 0L)
        } else {
            // idea: calculate distance after bounce, later limit on at least 1m
            val dtl = acc.timestamp - time                      // nanos
            val dt = dtl * 1e-9f                                // seconds
            val ds = acc.values[0] * dt                         // delta speed
            val slowDown = Math.pow(0.5, 1.0*dt).toFloat()      // slow down factor
            val ns = (speed * slowDown) + ds                    // new speed
            val nd = if(where == Where.MIDWAY) { distance + speed * dt } else { 0f }
            if(speed > 0 && ns < 0) {
                // l <-- r
                return State(ns, nd, acc.timestamp, relTime + dtl, Where.RIGHT)
            } else if(speed < 0 && ns > 0) {
                // l --> r
                return State(ns, nd, acc.timestamp, relTime + dtl, Where.LEFT)
            } else {
                return State(ns, nd, acc.timestamp, relTime + dtl, Where.MIDWAY)
            }
        }
    }
}

data class ExternalState(val distance: Float, val time: Float, val speed: Float, val measures: Triple<Gravity, Stability, Frequency>?, val extra: JsonObject = JsonObject())
