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
class SocketServer(val acceleration: Observable<SensorEvent>, val gravity: Observable<SensorEvent>, port: Int, ip: String): WebSocketServer(InetSocketAddress(ip, port)) {
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
            val gravityFactor = gravity.map { gravityFunction(it.values.get(2)) }
            subscription = acceleration
                    .map { it.values.get(0) }
                    .withLatestFrom(gravityFactor, { a, b -> (a to b) })
            .subscribe({
                Log.i(javaClass.simpleName, "${it}")
//                sendToAll("${it}")
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
    }
    return ret
}

fun FloatArray?.string(): String {
    if(this == null) {
        return "[]"
    }
    return this.map { v -> String.format(Locale.US, "%5.3f", v) }.joinToString(prefix = "[", postfix = "]")
}