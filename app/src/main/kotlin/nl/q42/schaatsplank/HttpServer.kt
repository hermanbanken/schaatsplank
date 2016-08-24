package nl.q42.schaatsplank

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import rx.Observable
import timber.log.Timber
import java.io.*
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
        val uri = when (session.uri) {
            "/" -> "index.html"
            else -> { session.uri.trim('/') }
        }

        // releases/download/v4/crop.m4v &
        // releases/download/v4/film.mp4
        if(uri.indexOf("github/") == 0) {
            val os = ByteArrayOutputStream()
            val url = "https://github.com/hermanbanken/schaatsplank/${uri.removePrefix("github/")}"
            Log.i(javaClass.simpleName, "Downloading from Github: $url")
            val ins = Cache(context).stream(url)
            return newChunkedResponse(Status.OK, getMimeTypeForFile(uri), ins)
        }

        for (dir in arrayOf("", "vendor")) {
            if(uri.indexOf(dir) != 0) { continue }
            val path = "public_html/$dir".trim('/')
            val file = uri.substring(dir.length).trim('/')
            if(context.resources.assets.list(path).contains(file)) {
                val ins = context.resources.assets.open("$path/$file")
                return newChunkedResponse(Status.OK, mime(file), ins)
            }
        }
        return newFixedLengthResponse(session.uri)
    }

    private fun mime(file: String): String = when (file.substring(file.lastIndexOf(".")+1)) {
        "html" -> "text/html"
        "css" -> "text/css"
        "js" -> "text/javascript"
        "jpg" -> "image/jpeg"
        "jpeg" -> "image/jpeg"
        "m4v" -> "video/mp4"
        "mp4" -> "video/mp4"
        else -> { "application/octet-stream" }
    }

}