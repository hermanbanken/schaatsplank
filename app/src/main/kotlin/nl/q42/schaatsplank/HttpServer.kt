package nl.q42.schaatsplank

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import timber.log.Timber
import java.net.SocketException

class HttpServer(hostname: String, val port: Int, val context: Context): NanoHTTPD(hostname, port) {

    init {
        Timber.i("Running webserver on "+port)
    }

    override fun createServerRunnable(timeout: Int): ServerRunnable? {
        return super.createServerRunnable(timeout)
    }

    override fun serve(session: IHTTPSession?): Response? {
        Log.i(javaClass.simpleName, "received "+session?.method+" to "+session?.uri)
        if(session != null) {
            return handle(session)
        }
        return null
    }

    fun handle(session: IHTTPSession): Response {
        if (session.uri == "/") {
            return newChunkedResponse(Status.OK, "text/html", context.resources.openRawResource(R.raw.index))
        } else if (session.uri == "/app.js") {
            return newChunkedResponse(Status.OK, "text/javascript", context.resources.openRawResource(R.raw.app))
        } else if (session.uri == "/vue.js") {
            return newChunkedResponse(Status.OK, "text/javascript", context.resources.openRawResource(R.raw.vue))
        } else if (session.uri == "/ready.jpg") {
            return newChunkedResponse(Status.OK, "image/jpeg", context.resources.openRawResource(R.raw.ready))
        } else if (session.uri == "/style.css") {
            return newChunkedResponse(Status.OK, "text/css", context.resources.openRawResource(R.raw.style))
        } else if (session.uri == "/smoothie.js") {
            return newChunkedResponse(Status.OK, "text/javascript", context.resources.openRawResource(R.raw.smoothie))
        } else if (session.uri == "/crop.m4v") {
            return newChunkedResponse(Status.OK, "video/mp4", context.resources.openRawResource(R.raw.crop))
        } else {
            return newFixedLengthResponse(session.uri)
        }
    }

}