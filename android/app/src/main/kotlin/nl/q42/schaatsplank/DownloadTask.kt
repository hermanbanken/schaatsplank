package nl.q42.schaatsplank

import android.app.ProgressDialog
import android.content.Context
import android.os.AsyncTask
import android.os.PowerManager
import android.widget.Toast

import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * @author Herman Banken, Q42
 */
internal class DownloadTask(private val context: Context) : AsyncTask<String, Int, String>() {
    private var wakeLock: PowerManager.WakeLock? = null

    val dialog: ProgressDialog by lazy {
        val dialog: ProgressDialog
        dialog = ProgressDialog(context)
        dialog.setMessage("Downloading video's for first use. Video's are stored locally.")
        dialog.isIndeterminate = true
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        dialog.setCancelable(true)
        dialog
    }

    override fun onPreExecute() {
        super.onPreExecute()
        // take CPU lock to prevent CPU from going off if the user
        // presses the power button during download
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                javaClass.name)
        wakeLock?.acquire()
        dialog.show()
    }

    override fun onProgressUpdate(vararg progress: Int?) {
        super.onProgressUpdate(progress[0])
        // if we get here, length is known, now set indeterminate to false
        dialog.setIndeterminate(false)
        dialog.setMax(100)
        dialog.progress = progress[0] ?: 0
    }

    override fun onPostExecute(result: String?) {
        wakeLock?.release()
        dialog.dismiss()
        if (result != null)
            Toast.makeText(context, "Download error: " + result, Toast.LENGTH_LONG).show()
        else
            Toast.makeText(context, "File downloaded", Toast.LENGTH_SHORT).show()
    }

    /**
     * Destination: "/sdcard/file_name.extension"
     */
    override fun doInBackground(vararg args: String?): String {
        var input: InputStream? = null
        var output: OutputStream? = null
        var connection: HttpURLConnection? = null
        try {
            val url = URL(args[0])
            connection = url.openConnection() as HttpURLConnection
            connection.connect()

            // expect HTTP 200 OK, so we don't mistakenly save error report instead of the file
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return "Server returned HTTP " + connection.responseCode +" " + connection.responseMessage
            }

            // this will be useful to display download percentage might be -1: server did not report the length
            val fileLength = connection.contentLength

            // download the file
            input = connection.inputStream
            output = FileOutputStream(args[1])

            val data = ByteArray(4096)
            var total: Long = 0
            var count: Int = input!!.read(data)
            while (count != -1) {
                // allow canceling with back button
                if (isCancelled) {
                    input.close()
                    return "Cancelled"
                }
                total += count.toLong()
                // publishing the progress.... only if total length is known
                if (fileLength > 0) {
                    publishProgress((total * 100 / fileLength).toInt())
                }
                output.write(data, 0, count)

                count = input.read(data)
            }
        } catch (e: Exception) {
            return e.toString()
        } finally {
            try {
                if (output != null)
                    output.close()
                if (input != null)
                    input.close()
            } catch (ignored: IOException) {
            }

            if (connection != null)
                connection.disconnect()
        }
        return "Done"
    }
}