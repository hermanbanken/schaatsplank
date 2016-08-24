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
            try {
                val response = handle(session)
                Log.i(javaClass.simpleName, "Delivering response for ${session.uri}")
                return response
            } catch(e: Exception) {
                Log.e(javaClass.simpleName, "error in handle", e)
            }
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
            val url = "https://github.com/hermanbanken/schaatsplank/${uri.removePrefix("github/")}"
            Log.i(javaClass.simpleName, "Downloading from Github: $url")
            val (file, ins) = Cache(context).stream(url)

            val fileLen = file.length()

            // Calculate eTag
            val eTag = Integer.toHexString((file.absolutePath + file.lastModified() + "" + fileLen).hashCode())

            val rangeHeader = session.headers["range"]
            val (range, length) = if(rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                val range = rangeHeader
                        .removePrefix("bytes=")
                        .split('-', limit = 2)
                        .map { if(it.isEmpty()) fileLen else it.toLong() }
                val start = range.getOrElse(0, { 0 })
                val end = range.getOrElse(1, { fileLen })
                Log.i(javaClass.simpleName, "Sending range $start - ${end-1} with length ${end - start}")
                (start to end) to (end - start)
            } else {
                null to fileLen
            }

            // https://github.com/NanoHttpd/nanohttpd/issues/232
            val mime = getMimeTypeForFile(uri)
            val response: Response
            if(range != null && range.first >= 0) {
                if(range.first >= file.length()) {
                    response = newFixedLengthResponse(Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "")
                    response.addHeader("Content-Range", "bytes 0-0/$fileLen")
                } else {
                    val fis: InputStream = object : BufferedInputStream(ins) {
                        @Throws(IOException::class)
                        override fun available(): Int {
                            return (range.second - range.first).toInt()
                        }
                    }
                    fis.skip(range.first)
                    response = newFixedLengthResponse(Status.PARTIAL_CONTENT, mime, fis, length)
                    response.addHeader("Content-Length", "$length")
                    response.addHeader("Content-Range", "bytes ${range.first}-${range.second - 1} / $fileLen")
                }
            } else {
                if(eTag == session.headers["if-none-match"]) {
                    response = newFixedLengthResponse(Status.NOT_MODIFIED, mime, "")
                } else {
                    response = newFixedLengthResponse(Status.OK, mime, ins, fileLen)
                    response.addHeader("Content-Length", "$fileLen")
                    response.addHeader("ETag", eTag)
                }
            }

            response.addHeader("Accept-Ranges", "bytes");
            return response
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