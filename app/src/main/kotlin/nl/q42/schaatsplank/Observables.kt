package nl.q42.schaatsplank

import android.util.Log
import rx.Observable

/**
 * @author Herman Banken, Q42
 */

fun <T,R> Observable<T>.sliding(op: (T, T) -> R): Observable<R> {
    return Observable.create<R> { sub ->
        var last: T? = null
        this.subscribe({ next ->
            val l = last
            if(l != null) {
                sub.onNext(op(l, next))
            }
            last = next
        }, { e -> sub.onError(e) }, { sub.onCompleted() })
    }
}

fun <T> Observable<T>.debug(key: String? = null, op: ((Any?) -> String)? = null): Observable<T> {
    return this.doOnEach { n ->
        return@doOnEach
        // Disabled
        val message =
                if(op != null && n.hasValue()) {
                    try {
                        if (key == null) "$n" else "$key: ${op(n.value)}"
                    } catch(_: Exception) {
                        "$key: exception while determining value"
                    }
                } else {
                    if (key == null) "$n" else "$key: $n"
                }
        try {
            Log.i(javaClass.simpleName, message)
        } catch(_: Exception) {
            print(message)
        }
    }
}