package nl.q42.schaatsplank

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.view.WindowManager
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.typeToken
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.image
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.subscriptions.CompositeSubscription
import java.util.concurrent.TimeUnit

/**
 * @author Herman Banken, Q42
 */
class Schaatsplank: Activity() {

    var disposable: Subscription? = null
    var connection: ServiceConnection? = null
    val gson = Gson()

    override fun onStart() {
        super.onStart()
        connection = object: ServiceConnection {
            override fun onServiceDisconnected(className: ComponentName?) {
                disposable?.unsubscribe()
                disposable = null
            }

            override fun onServiceConnected(className: ComponentName?, binder: IBinder?) {
                val instance = (binder as SchaatsplankService.Companion).instance
                if(instance != null && disposable == null) {
                    disposable = CompositeSubscription(
                        instance.logs
                            .observeOn(AndroidSchedulers.mainThread())
                            .startWith("ready")
                            .subscribe({
                                data.text = it
                            }, {
                                data.text = "error $it"
                            }, {
                                data.text = "completed"
                            }),
                        Observable.combineLatest(instance.address, instance.clients.startWith(0), { a, b -> a to b })
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe { pair ->
                                val (address: String?, count: Int) = pair
                                if(address == null) {
                                    textView.text = "1. Connect to WiFi"
                                } else if(count == 0) {
                                    textView.text = "1. $address"
                                } else {
                                    textView.text = "1. Connected!"
                                }
                                clients.text  = "$count"
                                instruction.visibility = if(count > 0) View.VISIBLE else View.GONE
                                clientsIcon.visibility = if(count > 0) View.VISIBLE else View.GONE
                                clients.visibility = if(count > 0) View.VISIBLE else View.GONE
                            }
                            ,
                            instance.clientMessages.filter {
                                val json = gson.fromJson<JsonObject>(it)
                                json.has("event") && json.get("event").asString == "start"
                            }
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe {
                                instruction.image = resources.getDrawable(R.drawable.started, theme)
                            }
//                        instance.output
//                                ?.throttleFirst(200, TimeUnit.MILLISECONDS)
//                                ?.observeOn(AndroidSchedulers.mainThread())
//                                ?.subscribe {
//
//                                }
                    )
                } else {
                    Log.i(javaClass.simpleName, "No instance of service available")
                }
            }
        }
        startService(Intent(this, SchaatsplankService::class.java))
        bindService(Intent(this, SchaatsplankService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onStop() {
        super.onStop()
        if(disposable != null) {
            unbindService(connection)
        }
    }
}
