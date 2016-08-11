package nl.q42.schaatsplank

import android.hardware.SensorEvent
import android.util.Log
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import rx.Observable
import rx.Subscription
import java.net.InetSocketAddress
import java.util.*

/**
 * @author Herman Banken, Q42
 */
class SocketServer(val input: Observable<SensorEvent>, port: Int, ip: String): WebSocketServer(InetSocketAddress(ip, port)) {
    private var subscription: Subscription? = null

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
            val gravity = input.map { it.values }.lowpass(1f/20f)
            val rest = gravity.publish { it.zipWith(input.skipUntil(it), { g, i -> i.values.minus(g) }) }

            gravity.zipWith(input.skipUntil(gravity), { g, i -> i.values to g })
                .subscribe { Log.i(javaClass.simpleName, "${it.first.string()} ${it.second.string()}") }

            val speed = input.map { it.values }.scan(0f, { speed, arr ->
                speed + arr[0]
            }).map { "[$it]" }

            subscription = rest.subscribe({
                sendToAll("${it.string()}")
            }, {
                Log.e(javaClass.simpleName, "rx error", it)
            })
        }
    }

    override fun onMessage(conn: WebSocket?, message: String?) {}
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
}

infix fun FloatArray.minus(other: FloatArray): FloatArray {
    if (this.size != other.size) {
        return FloatArray(0)
    }
    val ret = FloatArray(this.size)
    for  (i in 0..(this.size-1)) {
        ret[i] = this[i] - other[i]
//        Log.i(javaClass.simpleName, "${this[i]} - ${other[i]} = ${ret[i]}")
    }
    return ret
}

fun FloatArray?.string(): String {
    if(this == null) {
        return "[]"
    }
    return this.map { v -> String.format(Locale.US, "%5.3f", v) }.joinToString(prefix = "[", postfix = "]")
}