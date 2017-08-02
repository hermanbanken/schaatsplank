package nl.q42.schaatsplank

import rx.Observable
import rx.functions.Func2

/**
 * @author Herman Banken, Q42
 */
object Algorithm {

    fun match(match: Match, obs: Observable<Pair<State,Gravity>>): Observable<ExternalState> {
        val frequency = obs
            .map { it.first }
            .filter { it.where != Where.MIDWAY }
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
            .map { dur ->
                if(dur.first == 0f) return@map Frequency(0f)
                else return@map Frequency(1f / dur.first, dur.second.relTime * 1e-9f)
            }
            .filter { it.f != 0f }
//            .debug("freq", { f -> (f as Frequency).f.format(3) + "Hz" })

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
//        .debug { s -> (s as Stability).factor.format(3) }

        val freqStab = freqStability.startWith(Stability(0f)).withLatestFrom(frequency.startWith(Frequency(0f)), { a, b -> a to b })

        return obs
            .withLatestFrom(freqStab, { a, b -> a.first to Triple(a.second, b.first, b.second) })
            .scan(ExternalState(0f, 0f, 0f, null)) { prev, next ->
                val (state, measures) = next
                val (shape: Gravity, stability: Stability, freq: Frequency) = measures
                val dt = (state.relTime * 1e-9f - prev.time)

                // calculate speed = prevSpeed + stabilityFactor * gravityFactor
                val a = (frequencyFunction(freq.f * 2) * shape.factor * 0.5f)
                val b = (stability.factor * 0.5f)
                val goodness = if(freq.f == 0f && stability.factor == 0f) {
                  0f
                } else if (freq.time == null || state.relTime * 1e-9f - freq.time < 1f) {
                    Math.min(1f, Math.max(0f, a + b + 0.1f))
                } else 0f
                val range = accelerationRange(prev.speed)
                val acc = range.forFactor(goodness)
                val speed = prev.speed + acc * dt
                val distance = prev.distance + speed * dt

                val s = ExternalState(distance, state.relTime * 1e-9f, speed, measures)
                s.extra.addProperty("gravityfactor", shape.factor)
                s.extra.addProperty("tilt", shape.tiltAngle)
                s.extra.addProperty("acc", acc)
                s
            }
            .publish { it
                // take until distance + 1
                .takeWhile { it.distance < match.distance }
                .concatWith(it.take(1).map { it.copy(distance = match.distance.toFloat()) })
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
        val maxSpeed = 25f
        val maxAcc = 4f
        val half = maxSpeed / maxAcc
        return if(speed < maxSpeed) {
            AccelerationRange(-speed / half, maxAcc - speed / half)
        } else {
            AccelerationRange(-maxSpeed / half, 0f)
        }
    }
}
