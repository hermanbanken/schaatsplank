package nl.q42.schaatsplank

import java.io.PipedInputStream
import java.util.*

/**
 * @author Herman Banken, Q42
 */
final class OverflowChunkedInputStream(val messages: HashMap<Int, String>): PipedInputStream() {
    var chunk: Int = 0;
    override fun read(buffer: ByteArray?, off: Int, len: Int): Int {
        if(!messages.containsKey(chunk)) {
            return 0
        }
        val current = messages.get(chunk)
        if(current == null) return 0
        current.withIndex().forEach {
            buffer?.fill(it.value.toByte(), it.index)
        }
        chunk++
        return current.length
    }
}