package nl.q42.schaatsplank

import android.util.Log
import rx.Observable
import rx.Producer
import rx.Subscriber
import timber.log.Timber

class LowPassOperator(val factor: Float): Observable.Operator<FloatArray, FloatArray> {
    init {
        if(factor < 0 || factor > 1) {
            throw IllegalArgumentException("Factor must be between 0 and 1")
        }
    }

    class LowPassSubscriber(val factor: Float, val next: Subscriber<in FloatArray>?): Subscriber<FloatArray>(next) {
        var stabilized = FloatArray(0)
        var stable = 0f
        val rfactor = 1f - factor

        init {
            Log.i(javaClass.simpleName, "Factor: $factor, 1-f: $rfactor")
        }

        override fun onNext(t: FloatArray?) {
            if(t == null) return
            if(stabilized.size == t.size) {
                for (i in 0..(stabilized.size-1)) {
                    stabilized[i] = stabilized[i] * rfactor + t[i] * factor
                }
                if(stable < 1) {
                    stable += factor
                } else {
                    next?.onNext(stabilized)
                }
            } else {
                stable += factor
                stabilized = t.copyOf()
            }
        }

        override fun onError(e: Throwable?) {
           next?.onError(e)
        }

        override fun onCompleted() {
            next?.onCompleted()
        }

        override fun setProducer(p: Producer?) {
            next?.setProducer(p)
        }
    }

    override fun call(next: Subscriber<in FloatArray>?): Subscriber<in FloatArray>? {
        return LowPassSubscriber(factor, next)
    }

}


fun Observable<FloatArray>.lowpass(factor: Float): Observable<FloatArray> {
    return this.scan(0f to FloatArray(0), { memo, t ->
        if(t == null) return@scan memo
        val (stable, stabilized) = memo
        if(stabilized.size == t.size) {
            val copy = stabilized.copyOf()
            for (i in 0..(stabilized.size-1)) {
                copy[i] = stabilized[i] * (1f - factor) + t[i] * factor
            }
            if(stable < 1) {
                return@scan (stable + factor to copy)
            } else {
                return@scan (stable to copy)
            }
        } else {
            return@scan (stable + factor to t)
        }
    })
    .filter { it.first >= 1 }
    .map { it.second }
    .filter { it.size > 0 }
}

fun Observable<Float>.lowpassSingle(factor: Float): Observable<Float> {
    return this.scan(0f to 0f, { memo, t ->
        val (stable, stabilized) = memo
        val next = stabilized * (1f - factor) + t * factor
        if(stable < 1) {
            return@scan (stable + factor to next)
        } else {
            return@scan (stable to next)
        }
    })
    .filter { it.first >= 1 }
    .map { it.second }
}
