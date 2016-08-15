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

    val gson = Gson()
    val log = Store.read(context).toMutableList()
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

        var state: ExternalState = ExternalState(0f, 0f, 0f, null)

        observable = startRequests.switchMap { match ->
            val messages = arrayOf(
                "Skaters, go to the start!",
                "Ready?!",
                "Start!").map { Message(it) }
            Observable.interval(1, 3, TimeUnit.SECONDS)
                .zipWith(messages.toObservable(), { t, m -> m })
                .map { gson.toJson(it) }
                .concatWith(Observable.just("""{ "event": "start" }"""))
                .concatWith(stateEvents
                    .onBackpressureDrop()
                    .match(match)
                    .doOnNext { state = it }
                    .map { gson.toJson(it) }
                )
                .concatWith(Observable.just("""{ "event": "done" }"""))
                .doOnCompleted {
                    val result = match.copy(number = log.size, result = state.time)
                    log.add(result)
                    Store.write(context, result)
                }
                .takeUntil(stopRequests)
        }
        .mergeWith(stdOut)
    }

    fun receive(message: String) {
        val obj = gson.fromJson<JsonObject>(message, typeToken<JsonObject>())
        if(obj.has("event") && obj.get("event").asString == "start") {
            val name = if(obj.has("name")) obj.get("name").asString else "anonymous"
            val mail = if(obj.has("mail")) obj.get("mail").asString else null
            val dist = obj.get("distance").asInt
            startRequests.onNext(Match(name = name, email = mail, distance = dist))
        }
        if(obj.has("event") && obj.get("event").asString == "stop") {
            stopRequests.onNext(Unit)
        }
        if(obj.has("event") && obj.get("event").asString == "name") {
            val m = log.removeAt(log.size - 1).copy(name = obj.get("name").asString)
            log.add(m)
            store()
        }
        if(obj.has("event") && obj.get("event").asString == "clear_ranking") {
            Store.overwrite(context, "")
            store()
        }
    }

    fun store() {
        Store.overwrite(context, log.map { gson.toJson(it) }.joinToString("\n"))
        stdOut.onNext("""{ "event": "clear" }""")
        log.forEach {
            stdOut.onNext(gson.toJson(it))
        }
    }

    fun onConnect(): Observable<String> {
        val log = Store.read(context)
        return Observable.from(
            arrayOf("""{ "event": "clear" }""").plus(log.map { gson.toJson(it) })
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


enum class Where {
    LEFT, MIDWAY, RIGHT
}

data class Match(val number: Int = -1, val name: String, val email: String? = null, val distance: Int = 500, val result: Float? = 0f)
data class Message(val message: String, val event: String = "message")
