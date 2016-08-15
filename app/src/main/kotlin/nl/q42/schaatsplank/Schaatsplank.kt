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
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import fi.iki.elonen.NanoHTTPD
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.contentView
import rx.android.schedulers.AndroidSchedulers
import rx.subjects.BehaviorSubject
import timber.log.Timber
import java.io.IOException
import java.math.BigInteger
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket

/**
 * @author Herman Banken, Q42
 */
class Schaatsplank: Activity(), SensorEventListener, OnTouchListener {
    private var sensorManager: SensorManager? = null
    private var gravSensor: Sensor? = null
    private var acclSensor: Sensor? = null
    private var httpServer: HttpServer? = null
    private var socketServer: SocketServer? = null

    private val acceleration: BehaviorSubject<SensorEvent> = BehaviorSubject.create()
    private val gravity: BehaviorSubject<SensorEvent> = BehaviorSubject.create()
    private val finger: BehaviorSubject<Float> = BehaviorSubject.create()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        acclSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        gravSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
    }

    override fun onResume() {
        super.onResume()

        val wifiMgr = getSystemService(WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiMgr.connectionInfo
        val ip = wifiInfo.ipAddressString()

        Timber.i("SensorManager $sensorManager")
        Timber.i("Sensor $gravSensor")
        sensorManager?.registerListener(this, gravSensor, SensorManager.SENSOR_DELAY_GAME)
        sensorManager?.registerListener(this, acclSensor, SensorManager.SENSOR_DELAY_GAME)
        contentView?.setOnTouchListener(this)

        Thread {
            run {
                for (port in 8080..8090) {
                    Log.i(javaClass.simpleName, "HTTP: Trying port $ip:$port")
                    if(available(port, ip)) {
                        Log.i(javaClass.simpleName, "HTTP: Selected $ip:$port")
                        runOnUiThread {
                            textView.text = "Schaatsplank. Surf to http://$ip:$port/"
                        }
                        httpServer = HttpServer(ip, port, this)
                        httpServer?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                        break
                    }
                }
                if(httpServer == null) {
                    textView.text = "Cant open ports 8080-8090. App might be started multiple times. Please close all instances."
                }
                for (port in 8080..8090) {
                    Log.i(javaClass.simpleName, "WS: Trying port $ip:$port")
                    if(available(port, ip)) {
                        Log.i(javaClass.simpleName, "WS: Selected $ip:$port")
                        socketServer = SocketServer(
                                this,
                                acceleration,
                                gravity,
                                finger,
                                port, ip)
                        socketServer?.start()
                        break
                    }
                }
                if(socketServer == null) {
                    runOnUiThread {
                        textView.text = "Cant open ws ports 8080-8090. App might be started multiple times. Please close all instances."
                    }
                }
            }
        }.start()
    }

    override fun onPause() {
        socketServer?.stop()
        httpServer?.stop()
        super.onPause()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        data.text = "Accuracy changed $accuracy"
        Timber.i("Accuracy changed $accuracy")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if(event?.sensor == gravSensor) {
            gravity.onNext(event)
        }
        if(event?.sensor == acclSensor) {
            acceleration.onNext(event)
        }
    }

    override fun onTouch(view: View?, event: MotionEvent?): Boolean {
        if(event?.action == MotionEvent.ACTION_DOWN || event?.action == MotionEvent.ACTION_MOVE) {
            val x = event?.x?.toFloat()
            if (x != null && view != null) {
                finger.onNext(x / view.width)
                return true
            }
        }
        return false
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
