package nl.q42.schaatsplank

import android.hardware.SensorEvent
import android.util.Log
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import rx.Observable
import rx.Subscription
import java.net.InetSocketAddress

/**
 * @author Herman Banken, Q42
 */
class SocketServer(val input: Observable<SensorEvent>, port: Int, ip: String): WebSocketServer(InetSocketAddress(ip, port)) {
    private var subscription: Subscription? = null

    override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
        Log.i("", "conn: "+conn)
        Log.i("", "handshake: "+handshake)
        conn?.send("welcome")
        if(conn == null) return

    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        if(conn == null) return
    }

    override fun start() {
        Log.i(javaClass.simpleName, "Start")
        super.start()
        ensureSubscribed()
    }

    override fun run() {
        Log.i(javaClass.simpleName, "Run")
        super.run()
        ensureSubscribed()
    }

    override fun stop(timeout: Int) {
        Log.i(javaClass.simpleName, "Stop")
        subscription?.unsubscribe()
        subscription = null
        super.stop(timeout)
    }

    fun ensureSubscribed() {
        if(subscription == null) {
            subscription = input.subscribe({
                val values = it.values.string()
                sendToAll(values)
            }, {
                Log.e(javaClass.simpleName, "rx error", it)
            })
        }
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
//        throw UnsupportedOperationException()
    }

    override fun onError(conn: WebSocket?, ex: Exception?) {
//        throw UnsupportedOperationException()
    }

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