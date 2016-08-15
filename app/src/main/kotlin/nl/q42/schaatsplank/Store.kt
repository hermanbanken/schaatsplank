package nl.q42.schaatsplank

import android.content.Context
import android.util.Log
import com.github.salomonbrys.kotson.typeToken
import com.google.gson.Gson
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException

/**
 * @author Herman Banken, Q42
 */
object Store {
    private val gson: Gson = Gson()

    fun read(context: Context): List<Match> {
        val file = File(context.filesDir, "ranking.txt")
        if(!file.exists()) {
            return listOf()
        }
        val reader = FileReader(file)
        try {
            return reader.readLines().flatMap {
                val match = gson.fromJson<Match?>(it, typeToken<Match>())
                (if(match != null) arrayOf(match) else arrayOf()).asIterable()
            }
        } catch(e: IOException) {
            Log.e(javaClass.simpleName, "error while reading", e)
            return listOf()
        } finally {
            reader.close()
        }
    }

    fun write(context: Context, result: Match) {
        val file = File(context.filesDir, "ranking.txt")
        try {
            val writer = FileWriter(file, true)
            if (file.length() > 0) {
                writer.appendln()
            }
            writer.append(gson.toJson(result))
            writer.flush()
            writer.close()
        } catch(e: IOException) {
            Log.e(javaClass.simpleName, "error while saving", e)
        }
    }


    fun overwrite(context: Context, body: String) {
        val file = File(context.filesDir, "ranking.txt")
        try {
            val writer = FileWriter(file, false)
            writer.append(body)
            writer.flush()
            writer.close()
        } catch(e: IOException) {
            Log.e(javaClass.simpleName, "error while saving", e)
        }
    }

}