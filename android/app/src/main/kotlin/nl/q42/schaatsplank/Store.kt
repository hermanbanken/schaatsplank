package nl.q42.schaatsplank

import android.content.Context
import android.util.Log
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.typeToken
import com.google.gson.Gson
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException

/**
 * @author Herman Banken, Q42
 */
class Store(val context: Context) {
    private val gson: Gson = Gson()
    private val data = mutableListOf<Match>()

    init {
        data.addAll(read(context))
    }

    val all: List<Match> get() = data

    fun add(match: Match): Match {
        val n = match.copy(number = data.size)
        data.add(n)
        sync()
        return n
    }

    fun update(match: Match) {
        if(match.number > 0 && match.number < data.size) {
            data[match.number] = match
        sync()
        }
    }

    fun remove(index: Int) {
        data.removeAt(index)
        sync()
    }

    fun clear() {
        data.clear()
        sync()
    }

    fun sync() {
        overwrite(context, gson.toJson(data))
    }
    
    private fun read(context: Context): List<Match> {
        val file = File(context.filesDir, "ranking.txt")
        if(!file.exists()) {
            return listOf()
        }
        val reader = FileReader(file)
        try {
            return gson.fromJson<List<Match>>(reader)
        } catch(e: IOException) {
            Log.e(javaClass.simpleName, "error while reading", e)
            return listOf()
        } finally {
            reader.close()
        }
    }

    private fun overwrite(context: Context, body: String) {
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