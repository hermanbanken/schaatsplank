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
import kotlinx.android.synthetic.main.activity_main.*
import rx.Subscription
import rx.subjects.BehaviorSubject
import rx.subjects.Subject
import timber.log.Timber
import java.math.BigInteger
import java.net.InetAddress
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val wifiMgr = getSystemService(WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiMgr.connectionInfo
        val ip = wifiInfo.ipAddressString()

        textView.text = "Schaatsplank. Surf to "+ip+" port 8080"

        httpServer = HttpServer(this)
//        socketServer = SocketServer(values)
    }

    override fun onResume() {
        super.onResume()
        Timber.i("SensorManager %s", sensorManager.toString())
        Timber.i("Sensor %s", sensor.toString())
        sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        disposable = values.subscribe { event ->
//            httpServer?.send(event)
            data.text = String.format("Sensor changed %s", event.values.string())
            Timber.i("Sensor changed %s", event.values.string())
        }
    }

    override fun onPause() {
        disposable?.unsubscribe()
        disposable = null
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