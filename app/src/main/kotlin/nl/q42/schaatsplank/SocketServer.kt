package nl.q42.schaatsplank

import android.content.Context
import android.hardware.SensorEvent
import android.os.Looper
import android.util.Log
import com.github.salomonbrys.kotson.typeToken
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.lang.kotlin.PublishSubject
import rx.lang.kotlin.toObservable
import rx.schedulers.Schedulers
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.TimeUnit

enum class Where {
    LEFT, MIDWAY, RIGHT
}

data class Match(val number: Int = -1, val name: String, val email: String? = null, val distance: Int = 500, val result: Float? = 0f)
data class Message(val message: String, val event: String = "message")

/**
 * @author Herman Banken, Q42
 */
class SocketServer(val context: Context, val acceleration: Observable<SensorEvent>, val gravity: Observable<SensorEvent>, port: Int, ip: String): WebSocketServer(InetSocketAddress(ip, port)) {
    private var subscription: Subscription? = null
    val gson = Gson()
    val startRequests = PublishSubject<Match>()
    val stopRequests = PublishSubject<Unit>()
    val log = read(context).toMutableList()

    override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
        if(conn == null) return
        val log = read(context)
        conn.send("""{ "event": "clear" }""")
        log.forEach {
            conn.send(gson.toJson(it))
        }

    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        if(conn == null) return
    }

    override fun start() {
        super.start()
        ensureSubscribed()
    }

    override fun run() {
        super.run()
        ensureSubscribed()
    }

    override fun stop(timeout: Int) {
        subscription?.unsubscribe()
        subscription = null
        super.stop(timeout)
    }

    fun ensureSubscribed() {
        if(subscription == null) {
            val gravityFactor = gravity.map { Algorithm.gravityFunction(it.values.get(2)) }
            val states =  acceleration
                .scan(State(0f, 0f, 0L), { speed, acc -> speed.then(acc) })
                .publish { Observable.merge(
                    it.filter { it.where != Where.MIDWAY }.throttleFirst(500, TimeUnit.MILLISECONDS),
                    it.filter { it.where == Where.MIDWAY }.throttleFirst(100, TimeUnit.MILLISECONDS)
                ) }
                .onBackpressureBuffer(100, { Log.i(javaClass.simpleName, "Publish buffer overflow") })

            val stateEvents = states
                .withLatestFrom(gravityFactor, { state, gravity -> state to Gravity(gravity) })

            var state: ExternalState = ExternalState(0f, 0f, 0f, null)
            val matches = startRequests.switchMap { match ->
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
                        write(context, result)
                    }
                    .takeUntil(stopRequests)
            }

            matches
                .doOnError {
                    Log.e(javaClass.simpleName, "error", it)
                    sendToAll("""{ "event": "error", "error": "$it" }""")
                }
                .onBackpressureBuffer(1000, {
                    Log.e(javaClass.simpleName, "too much backpressure!!")
                })
                .subscribeOn(Schedulers.newThread())
                .observeOn(Schedulers.newThread())
                .retry()
                .subscribe({ sendToAll(it) })
        }
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
        if(message == null) return
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
            overwrite(context, "")
            store()
        }
    }

    fun store() {
        overwrite(context, log.map { gson.toJson(it) }.joinToString("\n"))
        sendToAll("""{ "event": "clear" }""")
        log.forEach {
            sendToAll(gson.toJson(it))
        }
    }

    override fun onError(conn: WebSocket?, ex: Exception?) {}

    /**
     * Sends <var>text</var> to all currently connected WebSocket clients.
     *
     * @param text
     *            The String to send across the network.
     * @throws InterruptedException
     *             When socket related I/O errors occur.
     */
    fun sendToAll( text: String ) {
        val con: Collection<WebSocket> = connections();
        synchronized ( con ) {
            for(c in con) {
                c.send( text );
            }
        }
    }

    fun read(context: Context): List<Match> {
        val file = File(context.filesDir, "ranking.txt")
        if(!file.exists()) {
            return listOf()
        }
        val reader = FileReader(file)
        try {
            return reader.readLines().flatMap {
                val match = gson.fromJson<Match?>(it, typeToken<Match>())
                (if(match != null) arrayOf(match) else arrayOf()).asIterable()
            }
        } catch(e: IOException) {
            Log.e(javaClass.simpleName, "error while reading", e)
            return listOf()
        } finally {
            reader.close()
        }
    }

    fun write(context: Context, result: Match) {
        val file = File(context.filesDir, "ranking.txt")
        try {
            val writer = FileWriter(file, true)
            if (file.length() > 0) {
                writer.appendln()
            }
            writer.append(gson.toJson(result))
            writer.flush()
            writer.close()
        } catch(e: IOException) {
            Log.e(javaClass.simpleName, "error while saving", e)
        }
    }


    fun overwrite(context: Context, body: String) {
        val file = File(context.filesDir, "ranking.txt")
        try {
            val writer = FileWriter(file, false)
            writer.append(body)
            writer.flush()
            writer.close()
        } catch(e: IOException) {
            Log.e(javaClass.simpleName, "error while saving", e)
        }
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
