package nl.q42.schaatsplank

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import kotlinx.android.synthetic.main.activity_main.*
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.subscriptions.CompositeSubscription

/**
 * @author Herman Banken, Q42
 */
class Schaatsplank: Activity() {

    var disposable: Subscription? = null
    var connection: ServiceConnection? = null

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
                            .scan("", { p, n -> n + "\n" + p })
                            .startWith("ready")
                            .subscribe({
                                data.text = it
                            }, {
                                data.text = "error $it"
                            }, {
                                data.text = "completed"
                            }),
                        instance.address
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe {
                                Log.i(javaClass.simpleName, it)
                                textView.text = it
                            },
                        instance.clients
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe {
                                    clients.text = "$it"
                                }
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
