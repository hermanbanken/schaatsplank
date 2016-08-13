package nl.q42.schaatsplank

import org.junit.Assert
import org.junit.Test
import java.util.*

/**
 * @author Herman Banken, Q42
 */
class AlgorithmTest {

    @Test
    fun testDeceleration() {
        val ts = arrayOf(1389226989662781, 1389227009295958, 1389227029283094, 1389227049200177, 1389227069638719, 1389227089131583)
        val dts = ts.sliding({ a, b -> b - a })

        val result = dts.map { 1e-9f * it }.fold(2f, { speed, dt -> speed * Math.pow(0.90, 1.0*dt).toFloat() })
        Assert.assertEquals(1.5f, result)
//        Assert.assertArrayEquals(arrayOf(0L,0L,0L,0L,0L), dts.toTypedArray())

        /*
i: (0.014814815, state(speed=7.9466085, distance=-254.51645, time=1389226989662781))
i: (0.014814815, state(speed=13.957876, distance=-252.95628, time=1389227009295958))
i: (0.014814815, state(speed=21.287668, distance=-250.1665, time=1389227029283094))
i: (0.014814815, state(speed=28.188633, distance=-245.92662, time=1389227049200177))
i: (0.014814815, state(speed=34.744045, distance=-240.16527, time=1389227069638719))
i: (0.014814815, state(speed=37.769, distance=-233.39265, time=1389227089131583))
*/
    }

}

/*
public inline fun <T> Array<out T>.partition(predicate: (T) -> Boolean): Pair<List<T>, List<T>> {
    val first = ArrayList<T>()
    val second = ArrayList<T>()

 */
fun <T,R> Array<out T>.sliding(op: (T,T) -> R): List<R> {
    if(this.size < 2) return ArrayList<R>()
    val result: ArrayList<R> = ArrayList<R>(this.size - 1)
    for (i in 0..(this.size-2)) {
        result.add(op(this[i], this[i+1]))
    }
    return result
}