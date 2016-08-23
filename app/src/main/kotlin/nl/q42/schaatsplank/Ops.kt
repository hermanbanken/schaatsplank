package nl.q42.schaatsplank

import com.google.gson.JsonObject
import rx.Observable
import java.util.*

/**
 * @author Herman Banken, Q42
 */
fun Float.format(digits: Int) = java.lang.String.format("%.${digits}f", this)!!

infix fun FloatArray.minus(other: FloatArray): FloatArray {
    if (this.size != other.size) {
        return FloatArray(0)
    }
    val ret = FloatArray(this.size)
    for  (i in 0..(this.size-1)) {
        ret[i] = this[i] - other[i]
    }
    return ret
}

fun FloatArray?.string(): String {
    if(this == null) {
        return "[]"
    }
    return this.map { v -> String.format(Locale.US, "%5.3f", v) }.joinToString(prefix = "[", postfix = "]")
}

fun JsonObject.getOptString(key: String): String? {
    return if(this.has(key)) this.get(key).asString else null
}

enum class Where {
    LEFT, MIDWAY, RIGHT
}

data class Match(val number: Int = -1, val name: String, val email: String? = null, val distance: Int = 500, val result: Float? = 0f, val date: Date = Date())
data class Message(val message: String, val event: String = "message")
