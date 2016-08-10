package nl.q42.schaatsplank

import android.content.Context
import android.hardware.SensorEvent
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import timber.log.Timber
import java.io.PipedInputStream
import java.util.*

class HttpServer(val context: Context): NanoHTTPD(8080) {
    val output: HashMap<Int,String>
    val input: OverflowChunkedInputStream

    private class ChunkedInputStream(var chunks: Array<String>): PipedInputStream() {
        var chunk: Int = 0;
        override fun read(buffer: ByteArray?, off: Int, len: Int): Int {
            chunks[chunk].withIndex().forEach {
                buffer?.fill(it.value.toByte(), it.index)
            }
            return chunks[chunk++].length
        }
    }

    init {
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        Timber.i("Running webserver on 8080")
        output  = HashMap()
        input = OverflowChunkedInputStream(output)
    }

    fun send(event: SensorEvent) {
        val bytes = event.values.string()
        val nextKey = (output.keys.lastOrNull() ?: -2) + 2
        output.put(nextKey, bytes)
    }

    override fun serve(session: IHTTPSession?): Response? {
        Log.i(javaClass.simpleName, "replying to "+session?.method+" to "+session?.uri)
//        val pipedInputStream = ChunkedInputStream(arrayOf(
//            "some",
//            "thing which is longer than sixteen characters",
//            "whee!",
//            ""
//        ));
        if(session != null) {
            val response = handle(session)
            Log.i(javaClass.simpleName, "with "+response)
            return response
        }
        return null
    }

    fun handle(session: IHTTPSession): Response {
        if(session.uri == "/") {
            return newChunkedResponse(Status.OK, "text/html", context.resources.openRawResource(R.raw.index))
        } else if(session.uri == "/app.js") {
            return newChunkedResponse(Status.OK, "text/javascript", context.resources.openRawResource(R.raw.app))
        } else {
            return newFixedLengthResponse(session.uri)
        }
    }

}