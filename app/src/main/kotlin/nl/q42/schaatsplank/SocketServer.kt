package nl.q42.schaatsplank

import android.content.Context
import android.hardware.SensorEvent
import android.util.Log
import com.github.salomonbrys.kotson.typeToken
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import rx.Observable
import rx.Subscription
import rx.lang.kotlin.PublishSubject
import rx.lang.kotlin.toObservable
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.TimeUnit

enum class Where {
    LEFT, MIDWAY, RIGHT
}

data class Match(val name: String, val email: String? = null, val distance: Int = 500, val result: Float? = 0f)

data class Message(val message: String, val event: String = "message")

data class State(val speed: Float, val distance: Float, val time: Long, val where: Where = Where.MIDWAY) {
    fun then(acc: SensorEvent): State {
        if(time == 0L) {
            return State(speed, distance, acc.timestamp)
        } else {
            // idea: calculate distance after bounce, later limit on at least 1m
            val dt = (acc.timestamp - time) * 1e-9f
            val ds = acc.values[0] * dt
            val slowDown = Math.pow(0.5, 1.0*dt).toFloat()
            val ns = (speed * slowDown) + ds
            val nd = if(where == Where.MIDWAY) { distance + speed * dt } else { 0f }
            if(speed > 0 && ns < 0) {
                // l <-- r
                return State(ns, nd, acc.timestamp, Where.RIGHT)
            } else if(speed < 0 && ns > 0) {
                // l --> r
                return State(ns, nd, acc.timestamp, Where.LEFT)
            } else {
                return State(ns, nd, acc.timestamp, Where.MIDWAY)
            }
        }
    }
}

/**
 * @author Herman Banken, Q42
 */
class SocketServer(val context: Context, val acceleration: Observable<SensorEvent>, val gravity: Observable<SensorEvent>, val finger: Observable<Float>, port: Int, ip: String): WebSocketServer(InetSocketAddress(ip, port)) {
    private var subscription: Subscription? = null
    val gson = Gson()
    val startRequests = PublishSubject<Match>()

    override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
        if(conn == null) return
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
            val gravityFactor = gravity.map { gravityFunction(it.values.get(2)) }
            val states =  acceleration
                .scan(State(0f, 0f, 0L), { speed, acc -> speed.then(acc) })
                .publish { Observable.merge(
                    it.filter { it.where != Where.MIDWAY }.throttleFirst(500, TimeUnit.MILLISECONDS),
                    it.filter { it.where == Where.MIDWAY }.throttleFirst(100, TimeUnit.MILLISECONDS)
                ) }

            val stateEvents = states
                .withLatestFrom(gravityFactor, { state, gravity -> state to gravity })
                .map({
                    val (s, gravity) = it
                    return@map """{
                        "event": "position",
                        "where": "${s.where}",
                        "distance": "${s.distance}",
                        "speed": "${s.speed}",
                        "shape": "$gravity"
                        }"""
                 })

            val matches = startRequests.switchMap { match ->
                val messages = arrayOf(
                    "Skaters, go to the start!",
                    "Ready?!",
                    "Start!").map { Message(it) }
                Observable.interval(1, 3, TimeUnit.SECONDS)
                        .zipWith(messages.toObservable(), { t, m -> m })
                        .map { gson.toJson(it) }
                        .concatWith(stateEvents)
            }

            matches.subscribe({
                sendToAll(it)
//                Log.i(javaClass.simpleName, "${it}")
            }, {
                Log.e(javaClass.simpleName, "rx error", it)
            })
        }
    }


    fun gravityFunction(zAxis: Float): Float {
        val quad = -Math.pow(zAxis / 3.0 - 1, 4.0) / 7
        val duo = Math.pow(zAxis / 3.0 - 1, 2.0) / 2
        val sin = zAxis / 4
        return ((quad + duo + sin) / 4.6 + 0.5).toFloat()
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
        if(message == null) return
        write(context, message)
        val obj = gson.fromJson<JsonObject>(message, typeToken<JsonObject>())
        if(obj.has("event") && obj.get("event").asString == "start") {
            val name = if(obj.has("name")) obj.get("name").asString else "anonymous"
            val mail = if(obj.has("mail")) obj.get("mail").asString else null
            val dist = obj.get("distance").asInt
            startRequests.onNext(Match(name = name, email = mail, distance = dist))
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

    fun write(context: Context, body: String) {
        val file = File(context.filesDir, "ranking.txt")
        try {
            val writer = FileWriter(file, true)
            if (file.length() > 0) {
                writer.appendln()
            }
            writer.append(body)
            writer.flush()
            writer.close()
        } catch(e: IOException) {
            Log.e(javaClass.simpleName, "error while saving", e)
        }
    }
}

fun detect(values: List<Float>): Iterable<Int> {
    if(values.size < 2) emptyList<Int>()
    val half = values.size / 2
    val sum = values.reversed().zip(values).take(half).sumByDouble {
        if (it.first < 0 && it.second > 0) {
            return@sumByDouble  Math.pow(1.0 * it.first * it.second, 2.0) * -1
        } else if(it.first > 0 && it.second < 0) {
            return@sumByDouble Math.pow(1.0 * it.first * it.second, 2.0)
        } else {
            return@sumByDouble 0.0
        }
    }
    return arrayOf(sum.toInt()).asIterable()
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