package nl.q42.schaatsplank

import android.app.Activity
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
import android.net.wifi.WifiManager
import android.os.IBinder
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import rx.subjects.BehaviorSubject

/**
 * @author Herman Banken, Q42
 */
class SchaatsplankService: Service(), SensorEventListener {
    private var sensorManager: SensorManager? = null
    private var gravSensor: Sensor? = null
    private var acclSensor: Sensor? = null
    private var httpServer: HttpServer? = null
    private var socketServer: SocketServer? = null

    private val acceleration: BehaviorSubject<SensorEvent> = BehaviorSubject.create()
    private val gravity: BehaviorSubject<SensorEvent> = BehaviorSubject.create()
    val logs: BehaviorSubject<String> = BehaviorSubject.create()

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        acclSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        gravSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        SchaatsplankService.instance = this
    }

    override fun onBind(p0: Intent?): IBinder? {
        throw UnsupportedOperationException()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("SensorManager $sensorManager")
        log("Sensor $gravSensor")
        sensorManager?.registerListener(this, gravSensor, SensorManager.SENSOR_DELAY_GAME)
        sensorManager?.registerListener(this, acclSensor, SensorManager.SENSOR_DELAY_GAME)

//        this.registerReceiver(object : BroadcastReceiver() {
//            override fun onReceive(context: Context?, intent: Intent?) {
//                val extraWifiState: Int? = intent?.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
//                when(extraWifiState) {
//                    WifiManager.WIFI_STATE_DISABLED, WifiManager.WIFI_STATE_DISABLING -> stopServers()
//                    WifiManager.WIFI_STATE_ENABLED -> setupServers()
//                }
//                setupServers()
//            }
//        }, IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION))

        // Monitor connectivity status
        this.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val cm = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val hasWifi = cm?.allNetworks?.any {
                    val info = cm.getNetworkInfo(it)
                    info.isConnectedOrConnecting && info.type == ConnectivityManager.TYPE_WIFI
                } ?: false
                if(hasWifi) { setupServers() } else { stopServers() }
            }
        }, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))

        return super.onStartCommand(intent, flags, startId)
    }

    fun setupServers() {
        val wifiMgr = getSystemService(Activity.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiMgr.connectionInfo
        val ip = wifiInfo.ipAddressString()

        Thread {
            run {
                for (port in 8080..8090) {
                    log("HTTP: Trying port $ip:$port")
                    if(available(port, ip)) {
                        log("HTTP: Selected $ip:$port")
                        httpServer = HttpServer(ip, port, this)
                        httpServer?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
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
                        socketServer = SocketServer(
                                this,
                                acceleration,
                                gravity,
                                port,
                                ip)
                        socketServer?.start()
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
            gravity.onNext(event)
        }
        if(event?.sensor == acclSensor) {
            acceleration.onNext(event)
        }
    }

    companion object {
        var instance: nl.q42.schaatsplank.SchaatsplankService? = null
    }
}
