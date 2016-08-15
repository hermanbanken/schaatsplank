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
import rx.Subscription
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
class Schaatsplank: Activity() {

    var disposable: Subscription? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onResume() {
        super.onResume()
        val instance = SchaatsplankService.instance
        if(instance != null) {
            disposable = instance.logs
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { textView.text = it }
        }
    }

    override fun onPause() {
        disposable?.unsubscribe()
        super.onPause()
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
