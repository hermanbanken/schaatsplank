package nl.q42.schaatsplank

import android.content.Context
import android.hardware.SensorEvent
import android.util.Log
import com.github.salomonbrys.kotson.typeToken
import com.google.gson.Gson
import com.google.gson.JsonObject
import rx.Observable
import rx.Scheduler
import rx.android.schedulers.AndroidSchedulers
import rx.lang.kotlin.PublishSubject
import rx.lang.kotlin.toObservable
import rx.subjects.PublishSubject
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * @author Herman Banken, Q42
 */
class Run(val context: Context, val acceleration: Observable<SensorEvent>, val gravity: Observable<SensorEvent>, val scheduler: Scheduler = AndroidSchedulers.mainThread()) {

    val startRequests = PublishSubject<Match>()
    val stopRequests = PublishSubject<Unit>()

    val store = Store(context)
    val gson = Gson()
    val log = store.all
    private val stdOut: PublishSubject<String> = PublishSubject()
    val observable: Observable<String>

    init {
        val gravityFactor = gravity.map { Algorithm.gravityFunction(it.values.get(2)) }
        val states =  acceleration
            .scan(State(0f, 0f, 0L), { speed, acc -> speed.then(acc) })
            .publish { Observable.merge(
                it.filter { it.where != Where.MIDWAY }.throttleFirst(500, TimeUnit.MILLISECONDS, scheduler),
                it.filter { it.where == Where.MIDWAY }.throttleFirst(100, TimeUnit.MILLISECONDS, scheduler)
            ) }
            .onBackpressureBuffer(100, { Log.i(javaClass.simpleName, "Publish buffer overflow") })

        val stateEvents = states
            .withLatestFrom(gravityFactor, { state, gravity -> state to Gravity(gravity) })

        observable = startRequests.switchMap { request ->
            val match = store.add(request)
            val messages = arrayOf(
                "Skaters, go to the start!",
                "Ready?!",
                "Start!").map { Message(it) }
            Observable.interval(1, 3, TimeUnit.SECONDS)
                .zipWith(messages.toObservable(), { t, m -> m })
                .map { gson.toJson(it) }
                .concatWith(Observable.just("""{ "event": "start", "distance": ${match.distance} }"""))
                .concatWith(stateEvents
                    .onBackpressureDrop()
                    .match(match)
                    .publish {
                        it.last().map { state ->
                            val final = match.copy(result = state.time)
                            store.update(final)
                            gson.toJson(final)
                        }
                        .mergeWith(it.map { gson.toJson(it) })
                    })
                .concatWith(Observable.just("""{ "event": "done" }"""))
                .takeUntil(stopRequests)
        }
        .mergeWith(stdOut)
    }

    fun receive(message: String) {
        val obj = gson.fromJson<JsonObject>(message, typeToken<JsonObject>())
        if(obj.getOpt("event") == "start") {
            val name = if(obj.has("name")) obj.get("name").asString else ""
            val mail = if(obj.has("mail")) obj.get("mail").asString else null
            val dist = obj.get("distance").asInt
            startRequests.onNext(Match(name = name, email = mail, distance = dist))
        }
        if(obj.getOpt("event") == "stop") {
            stopRequests.onNext(Unit)
        }
        if(obj.getOpt("event") == "name" && obj.get("number") != null) {
            val number = obj.get("number").asInt
            if(number > store.all.size || number < 0) {
                return
            }
            val name = obj.getOpt("name") ?: store.all[number].name
            val updated = store.all[number].copy(name = name)
            store.update(updated)
            stdOut.onNext(gson.toJson(updated))
        }
        if(obj.getOpt("event") == "remove" && obj.get("number") != null) {
            val number = obj.get("number").asInt
            if(number > store.all.size || number < 0) {
                store.remove(number)
                stdOut.onNext("""{ "event": "remove", "number": $number }""")
            }
        }
        if(obj.getOpt("event") == "clear_ranking") {
            store.clear()
            stdOut.onNext("""{ "event": "clear" }""")
        }
    }

    fun onConnect(): Observable<String> {
        return Observable.from(
            arrayOf("""{ "event": "clear" }""").plus(store.all.map { gson.toJson(it) })
        )
    }
}

infix fun FloatArray.minus(other: FloatArray): FloatArray {
    if (this.size != other.size) {
        return FloatArray(0)
    }
    val ret = FloatArray(this.size)
    for  (i in 0..(this.size-1)) {
        ret[i] = this[i] - other[i]
    }
    return ret
}

fun FloatArray?.string(): String {
    if(this == null) {
        return "[]"
    }
    return this.map { v -> String.format(Locale.US, "%5.3f", v) }.joinToString(prefix = "[", postfix = "]")
}

fun JsonObject.getOpt(key: String): String? {
    return if(this.has(key)) this.get(key).asString else null
}

enum class Where {
    LEFT, MIDWAY, RIGHT
}

data class Match(val number: Int = -1, val name: String, val email: String? = null, val distance: Int = 500, val result: Float? = 0f, val date: Date = Date())
data class Message(val message: String, val event: String = "message")
