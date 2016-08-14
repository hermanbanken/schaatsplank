package nl.q42.schaatsplank

import org.junit.Assert
import org.junit.Test
import rx.Observable
import rx.lang.kotlin.subscriber
import rx.observers.TestSubscriber
import rx.schedulers.TestScheduler
import rx.schedulers.Timestamped
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * @author Herman Banken, Q42
 */
class AlgorithmTest {

//    @Test
    fun testDeceleration() {
        val ts = arrayOf(1389226989662781, 1389227009295958, 1389227029283094, 1389227049200177, 1389227069638719, 1389227089131583)
        val dts = ts.sliding({ a, b -> b - a })

        val result = dts.map { 1e-9f * it }.fold(2f, { speed, dt -> speed * Math.pow(0.90, 1.0*dt).toFloat() })
        Assert.assertEquals(1.5f, result)
    }

    @Test
    fun testMatchObservable() {
        val scheduler = TestScheduler()
        val start = System.nanoTime()
        val slag = 3000L
        val mock = Observable.interval(19, TimeUnit.MILLISECONDS, scheduler).map { i ->
            val mod = i % (slag / 19)
            val where = if(mod == 0L) Where.LEFT else if(mod == (slag / 2 / 19)) Where.RIGHT else Where.MIDWAY
            State(0f, 0f, start + i * 19e6.toLong(), i * 19e6.toLong(), where)
        }.map { it to 0.99f }

        val result = TestSubscriber<Pair<ExternalState,Float>>()
        mock.match(Match("unknown", distance = 500)).subscribe(result)
        scheduler.advanceTimeBy(2, TimeUnit.MINUTES)
        val final = result.onNextEvents.last()
        val accs = result.onNextEvents.map { it.first.speed to it.second }
        val tfinal = final.first.time
        Assert.assertTrue("tfinal > 36", tfinal > 36f)
        Assert.assertTrue("tfinal < 50", tfinal < 50f)
        result.assertCompleted()
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