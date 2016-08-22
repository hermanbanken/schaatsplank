package nl.q42.schaatsplank

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import rx.Observable
import rx.lang.kotlin.ReplaySubject
import rx.subjects.BehaviorSubject
import rx.subjects.ReplaySubject
import java.io.IOException
import java.math.BigInteger
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

/**
 * @author Herman Banken, Q42
 */
class SchaatsplankService: Service(), SensorEventListener {
    private var sensorManager: SensorManager? = null
    private var gravSensor: Sensor? = null
    private var acclSensor: Sensor? = null
    private var httpServer: HttpServer? = null
    private var socketServer: SocketServer? = null

    private val acceleration: BehaviorSubject<Event> = BehaviorSubject.create()
    private val gravity: BehaviorSubject<Event> = BehaviorSubject.create()
    val address: ReplaySubject<String?> = ReplaySubject(1)
    val logs: ReplaySubject<String> = ReplaySubject(10)
    val clients: ReplaySubject<Int> = ReplaySubject(1)
    val clientMessages: BehaviorSubject<String> = BehaviorSubject.create()

    var run: Run? = null
    var output: Observable<String>? = null

    override fun onCreate() {
        super.onCreate()
        log("created")
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        acclSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        gravSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)

        val run = Run(this.applicationContext, acceleration, gravity)
        this.run = run
        output = run.observable
                .doOnError {
                    Log.e(javaClass.simpleName, "error", it)
                }
                .retry()
                .publish().autoConnect()

        if (isSimulator()) {
            runSimulated()
        }
    }

    private fun  isSimulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic")
    }

    private fun runSimulated() {
        Observable.interval(19L, TimeUnit.MILLISECONDS)
                .map { it to Math.sin(it.toDouble() / 1000 * Math.PI) }
                .subscribe {
                    gravity.onNext(Event(it.first, floatArrayOf(0f, 0f, 8f)))
                    acceleration.onNext(Event(it.first, floatArrayOf(it.second.toFloat(), 0f, 0f)))
                }
    }

    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    override fun onBind(intent: Intent?): IBinder? {
        return Companion
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(SchaatsplankService.instance != null) {
            return START_STICKY
        }

        SchaatsplankService.instance = this
        log("Starting service")
        if(isSimulator()) {
            setupServers()
        } else {
            sensorManager?.registerListener(this, gravSensor, SensorManager.SENSOR_DELAY_GAME)
            sensorManager?.registerListener(this, acclSensor, SensorManager.SENSOR_DELAY_GAME)

            // Monitor connectivity status
            this.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val cm = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    val hasWifi = cm?.allNetworks?.any {
                        val info = cm.getNetworkInfo(it)
                        info.isConnectedOrConnecting && info.type == ConnectivityManager.TYPE_WIFI
                    } ?: false
                    log("Network status changed: ${if(hasWifi) "connected" else "disconnected"}")
                    if(hasWifi) { setupServers() } else {
                        address.onNext(null)
                        stopServers()
                    }
                }
            }, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
        }

        return START_STICKY
    }

    fun foreground(address: String) {
        val id = 4242;
        //The intent to launch when the user clicks the expanded notification
        val intent = Intent(this, Schaatsplank::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val pendIntent = PendingIntent.getActivity(this, 0, intent, 0)

        //This constructor is deprecated. Use Notification.Builder instead
        val notice = Notification.Builder(this)
            .setContentText("Running Schaatsplank at $address")
            .setAutoCancel(false)
            .setWhen(System.currentTimeMillis())
            .setContentIntent(pendIntent)
            .build()
        notice.flags = notice.flags or Notification.FLAG_NO_CLEAR
        startForeground(id, notice)
    }

    fun setupServers() {
        val ip = getIp()

        Thread {
            run {
                for (port in 8080..8090) {
                    log("HTTP: Trying port $ip:$port")
                    if(available(port, ip)) {
                        log("HTTP: Selected $ip:$port")
                        httpServer = HttpServer(ip, port, this)
                        httpServer?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                        address.onNext("Surf to http://$ip:$port/")
                        foreground("http://$ip:$port/")
                        break
                    }
                }
                if(httpServer == null) {
                    log("Cant open ports 8080-8090. App might be started multiple times. Please close all instances.")
                }
                for (port in 8080..8090) {
                    log("WS: Trying port $ip:$port")
                    if(available(port, ip)) {
                        Log.i(javaClass.simpleName, "WS: Selected $ip:$port")
                        val output = this.output
                        val run = this.run
                        if(output == null || run == null) break
                        socketServer = SocketServer(
                                this,
                                output,
                                { run.receive(it); clientMessages.onNext(it) },
                                { run.onConnect() },
                                port,
                                ip)
                        socketServer?.start()
                        socketServer?.clients?.subscribe { clients.onNext(it) }
                        break
                    }
                }
                if(socketServer == null) {
                    log("Cant open ws ports 8080-8090. App might be started multiple times. Please close all instances.")
                }
            }
        }.start()
    }

    fun log(value: String) {
        Log.i(javaClass.simpleName, value)
        logs.onNext(value)
    }

    fun stopServers() {
        socketServer?.stop()
        httpServer?.stop()
    }

    override fun onDestroy() {
        stopServers()
        super.onDestroy()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent?) {
        if(event?.sensor == gravSensor) {
            gravity.onNext(Event(event))
        }
        if(event?.sensor == acclSensor) {
            acceleration.onNext(Event(event))
        }
    }

    companion object: Binder() {
        var instance: nl.q42.schaatsplank.SchaatsplankService? = null
    }

    fun getIp(): String {
        if(isSimulator()) {
            val ifs = NetworkInterface.getNetworkInterfaces()
            return ifs.toList().flatMap {
                it.inetAddresses.toList()
                        .filter { !it.isLoopbackAddress }
                        .map { it.hostAddress }
            }
            .filter { it.indexOf(":") != 5 && it.indexOf(":") != 4 }
            .first()
        } else {
            val wifiMgr = getSystemService(Activity.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiMgr.connectionInfo
            val ip = wifiInfo.ipAddressString()
            return ip
        }
    }
}

/**
 * Get formatted IPv4 address
 * @see {@link http://stackoverflow.com/a/26915348/552203}
 */
fun WifiInfo.ipAddressString(): String {
    val bytes = BigInteger.valueOf(this.ipAddress.toLong()).toByteArray()
    bytes.reverse()
    // you must reverse the byte array before conversion
    val ip = InetAddress.getByAddress(bytes)
    return ip.hostAddress
}

/**
 * Checks to see if a specific port is available.
 *
 * @param port the port to check for availability
 */
fun available(port: Int, hostname: String? = null): Boolean {

    if (port < 8000 || port > 64000) {
        throw IllegalArgumentException("Invalid start port: $port");
    }

    var ss: ServerSocket? = null
    var ds: DatagramSocket? = null
    try {
        if(hostname == null) {
            ss = ServerSocket(port)
        } else {
            ss = ServerSocket(port, 0, InetAddress.getByName(hostname))
        }
        ss.reuseAddress = true;
        ds = DatagramSocket(port);
        ds.reuseAddress = true;
        return true;
    } catch (e: IOException) {
    } finally {
        ds?.close()
        try {
            ss?.close();
        } catch (e: IOException) {
            /* should not be thrown */
        }
    }
    return false;
}
