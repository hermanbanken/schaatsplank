package nl.q42.schaatsplank

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import kotlinx.android.synthetic.main.activity_main.*
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.subjects.BehaviorSubject
import rx.subjects.Subject
import timber.log.Timber
import java.io.IOException
import java.math.BigInteger
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.util.*

/**
 * @author Herman Banken, Q42
 */
class Schaatsplank: Activity(), SensorEventListener {
    private var sensorManager: SensorManager? = null
    private var sensor: Sensor? = null
    private val values: BehaviorSubject<SensorEvent> = BehaviorSubject.create()
    private var disposable: Subscription? = null
    private var httpServer: HttpServer? = null
    private var socketServer: SocketServer? = null
    var ip: String = "127.0.0.1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val wifiMgr = getSystemService(WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiMgr.connectionInfo
        val ip = wifiInfo.ipAddressString()
        this.ip = ip
    }

    override fun onResume() {
        super.onResume()
        Timber.i("SensorManager %s", sensorManager.toString())
        Timber.i("Sensor %s", sensor.toString())
        sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        disposable = values.subscribe { event ->
            data.text = String.format("Sensor changed %s", event.values.string())
            Timber.i("Sensor changed %s", event.values.string())
        }

        Thread {
            run {
                for (port in 8080..8090) {
                    Log.i(javaClass.simpleName, String.format("HTTP: Trying port %s:%d", ip, port))
                    if(available(port, ip)) {
                        Log.i(javaClass.simpleName, String.format("HTTP: Selected %s:%d", ip, port))
                        textView.text = "Schaatsplank. Surf to http://"+ip+":"+port+"/"
                        httpServer = HttpServer(ip, port, this)
                        httpServer?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                        break
                    }
                }
                if(httpServer == null) {
                    textView.text = "Cant open ports 8080-8090. App might be started multiple times. Please close all instances."
                }
                for (port in 8080..8090) {
                    Log.i(javaClass.simpleName, String.format("WS: Trying port %s:%d", ip, port))
                    if(available(port, ip)) {
                        Log.i(javaClass.simpleName, String.format("WS: Selected %s:%d", ip, port))
                        socketServer = SocketServer(values.subscribeOn(AndroidSchedulers.mainThread()), port, ip)
                        socketServer?.start()
                        break
                    }
                }
                if(socketServer == null) {
                    textView.text = "Cant open ws ports 8080-8090. App might be started multiple times. Please close all instances."
                }
            }
        }.start()
    }

    override fun onPause() {
        disposable?.unsubscribe()
        disposable = null
        socketServer?.stop()
        httpServer?.stop()
        super.onPause()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        data.text = String.format("Accuracy changed %d", accuracy)
        Timber.i("Accuracy changed %d", accuracy)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        values.onNext(event)
    }
}

fun FloatArray?.string(): String {
    if(this == null) {
        return "[]"
    }
    return this.map { v -> String.format(Locale.GERMAN, "%4.2f", v) }.joinToString(prefix = "[", postfix = "]")
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
    return ip.getHostAddress()
}

/**
 * Checks to see if a specific port is available.
 *
 * @param port the port to check for availability
 */
fun available(port: Int, hostname: String? = null): Boolean {

    if (port < 8000 || port > 64000) {
        throw IllegalArgumentException("Invalid start port: " + port);
    }

    var ss: ServerSocket? = null
    var ds: DatagramSocket? = null
    try {
        if(hostname == null) {
            ss = ServerSocket(port)
        } else {
            ss = ServerSocket(port, 0, InetAddress.getByName(hostname))
        }
        ss.setReuseAddress(true);
        ds = DatagramSocket(port);
        ds.setReuseAddress(true);
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
