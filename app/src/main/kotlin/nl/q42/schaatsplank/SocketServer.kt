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
class SocketServer(val input: Observable<SensorEvent>): WebSocketServer(InetSocketAddress(8081)) {
    private val subscriptions: HashMap<WebSocket, Subscription> = HashMap()

    override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
        if(conn == null) return
        Log.i("", "conn: "+conn)
        Log.i("", "handshake: "+handshake)
        conn.send("welcome")
        subscriptions.put(conn, input.subscribe {
            print(""+it)
        })
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        if(conn == null) return
        connections().remove(conn)
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
        throw UnsupportedOperationException()
    }

    override fun onError(conn: WebSocket?, ex: Exception?) {
        throw UnsupportedOperationException()
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