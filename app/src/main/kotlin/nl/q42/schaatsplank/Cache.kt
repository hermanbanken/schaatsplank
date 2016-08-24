package nl.q42.schaatsplank

import android.app.DownloadManager
import android.app.DownloadManager.Request
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.downloadManager
import org.jetbrains.anko.runOnUiThread
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.lang.kotlin.BehaviorSubject
import rx.schedulers.Schedulers
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.UnsupportedEncodingException
import java.net.URI
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * @author Herman Banken, Q42
 */
class Cache(val context: Context) {

    fun get(url: String): Observable<InputStream> {
        val subject = BehaviorSubject<InputStream>()
        val path = context.externalMediaDirs[0]
        val file = "schaatsplank-" + SimpleSHA1.SHA1(url).substring(0, 8) + "." + url.split('/').last()

        // Try cache
        val location = File(path, file)
        if(location.exists()) {
            return Observable.just(location.inputStream())
        }

        context.runOnUiThread {
            Toast.makeText(context, "Downloading video", Toast.LENGTH_SHORT).show()
        }

        // Download to cache
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            val request = Request(Uri.parse(url))
            request.setDescription("Caching ${url.split('/').last()}")
            request.setTitle("Schaatsplank download")
            request.setNotificationVisibility(Request.VISIBILITY_VISIBLE)
            request.setDestinationUri(Uri.fromFile(location))
            // get download service and enqueue file
            val manager = context.downloadManager
            var file_id: Long = 0
            context.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0L) ?: 0L
                    if (id != file_id) {
                        return
                    }
                    val query = DownloadManager.Query()
                    query.setFilterById(id)
                    val cursor = manager.query(query)

                    // it shouldn't be empty, but just in case
                    if (!cursor.moveToFirst()) {
                        subject.onError(RuntimeException("Empty row"))
                        return
                    }
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (DownloadManager.STATUS_SUCCESSFUL !== cursor.getInt(statusIndex)) {
                        subject.onError(RuntimeException("Download Failed ${cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON))}"))
                        return
                    }

                    val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    val uri = cursor.getString(uriIndex)
                    val file = File(URI(uri))
                    cursor.close()

                    Log.i(javaClass.simpleName, "Downloaded $uri")
                    if(file.exists()) {
                        subject.onNext(file.inputStream())
                        subject.onCompleted()
                    } else {
                        subject.onError(RuntimeException("File was gone"))
                    }
                }
            }, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            try {
                file_id = manager.enqueue(request)
            } catch (e: Exception) {
                print(e)
            }
        }
        // Download to cache
        else {
            val task = DownloadTask(context).execute(url, File(path, file).absolutePath)
            Observable.fromCallable { task.get() }
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .map { File(it).inputStream() }
                    .subscribe(subject)
        }
        return subject
    }

    fun stream(url: String): InputStream? {
        return get(url).observeOn(Schedulers.newThread()).toBlocking().first()
    }
}

object SimpleSHA1 {
    private fun convertToHex(data: ByteArray): String {
        val buf = StringBuilder()
        for (b in data) {
            var halfbyte = ((b.toInt() ushr 4) and 0x0F).toByte()
            var two_halfs = 0
            do {
                buf.append(if (0 <= halfbyte && halfbyte <= 9) ("0" + halfbyte) else ('a' + (halfbyte - 10)).toChar())
                halfbyte = (b.toInt() and 0x0F).toByte()
            } while (two_halfs++ < 1)
        }
        return buf.toString()
    }

    @Throws(NoSuchAlgorithmException::class, UnsupportedEncodingException::class)
    fun SHA1(text: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update(text.toByteArray(charset("iso-8859-1")), 0, text.length)
        val sha1hash = md.digest()
        return convertToHex(sha1hash)
    }
}