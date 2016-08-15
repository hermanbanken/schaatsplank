package nl.q42.schaatsplank

import android.content.Context
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import rx.Observable
import rx.Subscription
import rx.lang.kotlin.ReplaySubject
import java.net.InetSocketAddress

/**
 * @author Herman Banken, Q42
 */
class SocketServer(val context: Context, val broadcast: Observable<String>, val output: (String) -> Unit, val onConnect: () -> Observable<String>, port: Int, ip: String): WebSocketServer(InetSocketAddress(ip, port)) {
    private var subscription: Subscription? = null
    private val clientsSubject = ReplaySubject<Int>(1)
    val clients: Observable<Int> = clientsSubject

    override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
        clientsSubject.onNext(connections().size)
        if(conn == null) return
        onConnect().subscribe { conn.send(it) }
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        clientsSubject.onNext(connections().size)
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
            subscription = broadcast.subscribe { sendToAll(it) }
        }
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
        if(message == null) return
        output(message)
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
}
