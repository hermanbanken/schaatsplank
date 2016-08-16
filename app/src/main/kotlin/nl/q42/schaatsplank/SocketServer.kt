package nl.q42.schaatsplank

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import rx.Observable
import rx.Subscription
import rx.lang.kotlin.ReplaySubject
import rx.subscriptions.CompositeSubscription
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

/**
 * @author Herman Banken, Q42
 */
class SocketServer(val context: Context, val broadcast: Observable<String>, val output: (String) -> Unit, val onConnect: () -> Observable<String>, port: Int, ip: String): WebSocketServer(InetSocketAddress(ip, port)) {
    private var subscription: Subscription? = null
    private val clientsSubject = ReplaySubject<Int>(1)
    val clients: Observable<Int> = clientsSubject

    init {
        Log.i(javaClass.simpleName, "Started SocketServer $ip $port")
    }

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
        connections().forEach { it.close() }
        subscription?.unsubscribe()
        subscription = null
        super.stop(timeout)
    }

    fun ensureSubscribed() {
        if(subscription == null) {
            subscription = CompositeSubscription(
                broadcast.subscribe { sendToAll(it) },
                batteryObs().subscribe { sendToAll("""{ "event": "battery", "value": $it }""") },
                Observable.interval(3, TimeUnit.SECONDS).subscribe { sendToAll("""{ "event": "ping" }""") }
            )
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

    fun batteryObs(): Observable<Float> {
        return Observable.interval(30, TimeUnit.SECONDS).map { battery() }
    }

    fun battery(): Float {
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, ifilter)
        val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return level / scale.toFloat()
    }
}
