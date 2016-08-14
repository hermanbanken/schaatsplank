package nl.q42.schaatsplank

import android.hardware.SensorEvent
import rx.Observable
import rx.functions.Func2

/**
 * @author Herman Banken, Q42
 */
data class Frequency(val f: Float)
data class Gravity(val factor: Float)
data class Stability(val factor: Float)

data class State(val speed: Float, val distance: Float, val time: Long, val relTime: Long = 0, val where: Where = Where.MIDWAY) {
    fun then(acc: SensorEvent): State {
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

data class ExternalState(val distance: Float, val time: Float, val speed: Float, val measures: Triple<Gravity, Stability, Frequency>?)

object Algorithm {

    fun match(match: Match, obs: Observable<Pair<State,Gravity>>): Observable<ExternalState> {
        val frequency = obs
            .map { it.first }
            .filter { it.where != Where.MIDWAY }
            .debug("state")
            .scan<Pair<Float,State>>(2f to State(0f, 0f, 0L), Func2 { prev, state ->
                val pState = prev.second
                val dt = (state.relTime - pState.relTime) * 1e-9f
                val next = if (state.relTime != 0L) {
                    if (pState.where != state.where) dt else dt / 2
                } else {
                    0f
                }
                next to state
            })
            .map { it.first }
            .filter { it != 0f }
            .map { Frequency(1f / it) }
            .debug("freq", { f -> (f as Frequency).f.format(3) + "Hz" })

        // calculate frequency stability
        val freqStability = frequency.sliding { a, b ->
            // freq should be between 1Hz and 1/10Hz
            if(a.f < 0.1f || b.f < 0.1f || a.f > 1f || b.f > 1f) {
                0f
            } else {
                Math.min(a.f / b.f, b.f / a.f)
            }
        }
        .map { Stability(it) }
        .debug { s -> (s as Stability).factor.format(3) }

        val freqStab = freqStability.withLatestFrom(frequency, { a, b -> a to b })

        return obs
            .withLatestFrom(freqStab, { a, b -> a.first to Triple(a.second, b.first, b.second) })
            .scan(ExternalState(0f, 0f, 0f, null)) { prev, next ->
                val (state, measures) = next
                val (shape: Gravity, stability: Stability, freq: Frequency) = measures
                val dt = (state.relTime * 1e-9f - prev.time)

                // calculate speed = prevSpeed + stabilityFactor * gravityFactor
                val goodness = (frequencyFunction(freq.f * 2) * shape.factor * 0.5f) + (stability.factor * 0.5f)
                val acc = accelerationRange(prev.speed).forFactor(goodness)
                val speed = prev.speed + acc * dt
                val distance = prev.distance + speed * dt

                ExternalState(distance, state.relTime * 1e-9f, speed, measures)
            }
            .debug("external state")
            .publish { it
                // take until distance + 1
                .takeWhile { it.distance < match.distance }
                .concatWith(it.take(1))
            }
    }

    fun frequencyFunction(freqBothWays: Float): Float {
        val f = freqBothWays
        val a = - Math.pow(f / 2 - 0.4, 4.0) + 1
        val b = - Math.pow(f / 3.2 - 1.3, 2.0) + 0.6
        return (a + b).toFloat()
    }

    fun gravityFunction(zAxis: Float): Float {
        val quad = -Math.pow(zAxis / 3.0 - 1, 4.0) / 7
        val duo = Math.pow(zAxis / 3.0 - 1, 2.0) / 2
        val sin = zAxis / 4
        return ((quad + duo + sin) / 4.6 + 0.5).toFloat()
    }

    // max is acceleration, min is deceleration
    data class AccelerationRange(val min: Float, val max: Float) {
        fun forFactor(factor: Float): Float {
            if(factor > 1 || factor < 0) {
                throw IllegalArgumentException("Factor must always be between 0 and 1")
            }

            return min + (max - min) * factor
        }
    }
    fun accelerationRange(speed: Float): AccelerationRange {
        return if(speed < 10) {
            AccelerationRange(speed / 5.3f, 2f - speed / 5.3f)
        } else {
            AccelerationRange(10f / 5.3f, 2f - 10f / 5.3f)
        }
    }
}

fun Observable<Pair<State, Gravity>>.match(match: Match): Observable<ExternalState> {
    return Algorithm.match(match, this)
}

fun Float.format(digits: Int) = java.lang.String.format("%.${digits}f", this)
