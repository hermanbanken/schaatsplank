package nl.q42.schaatsplank

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.junit.Assert
import org.junit.Test
import rx.Observable
import rx.observers.TestSubscriber
import rx.schedulers.TestScheduler
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

//    @Test
    fun testPerfectSkater(): Float {
        val scheduler = TestScheduler()
        val start = System.nanoTime()
        val slag = 3000L
        val mock = Observable.interval(19, TimeUnit.MILLISECONDS, scheduler).map { i ->
            val mod = i % (slag / 19)
            val where = if(mod == 0L) Where.LEFT else if(mod == (slag / 2 / 19)) Where.RIGHT else Where.MIDWAY
            State(0f, 0f, start + i * 19e6.toLong(), i * 19e6.toLong(), where)
        }.map { it to Gravity(0.99f, 0f) }

        return checkObservable(scheduler, mock, 1)
    }

//    @Test
    fun testMediumSkater(): Float {
        val scheduler = TestScheduler()
        val start = System.nanoTime()

        var inc = 0
        var counter = 0
        val offsets = RandomRangeIterator(1200,1600)
        var offset = offsets.next()
        val mock = Observable.interval(19, TimeUnit.MILLISECONDS, scheduler).map { i ->
            counter += 1
            val where = if(counter > offset) {
                counter = 0
                offset = offsets.next()
                if(inc++ % 2 == 0) Where.LEFT else Where.RIGHT
            } else Where.MIDWAY
            State(0f, 0f, start + i * 19e6.toLong(), i * 19e6.toLong(), where)
        }.map { it to Gravity(0.6f, 0f) }

        return checkObservable(scheduler, mock, offset)
    }

//    @Test
    fun testDeepSkater(): Float {
        val scheduler = TestScheduler()
        val start = System.nanoTime()

        var inc = 0
        var counter = 0
        val offsets = RandomRangeIterator(1200,1600)
        var offset = offsets.next()
        val mock = Observable.interval(19, TimeUnit.MILLISECONDS, scheduler).map { i ->
            counter += 1
            val where = if(counter > offset) {
                counter = 0
                offset = offsets.next()
                if(inc++ % 2 == 0) Where.LEFT else Where.RIGHT
            } else Where.MIDWAY
            State(0f, 0f, start + i * 19e6.toLong(), i * 19e6.toLong(), where)
        }.map { it to Gravity(0.8f, 0f) }

        return checkObservable(scheduler, mock, offset)
    }

    @Test
    fun testRepeatedly() {
        val list = ArrayList<Float>()
        for (n in 0..40) {
            list.add(testDeepSkater())
        }
        print(list)
    }

    fun checkObservable(scheduler: TestScheduler, obs: Observable<Pair<State,Gravity>>, extra: Any): Float {
        val result = TestSubscriber<ExternalState>()
        obs.match(Match(0, "unknown", distance = 500)).subscribe(result)
        scheduler.advanceTimeBy(2, TimeUnit.DAYS)
        val final = result.onNextEvents.last()
        val accs = result.onNextEvents.map {
            it.extra.getOpt("goodness")?.asFloat to
            it.extra.getOpt("range_min")?.asFloat to
            it.extra.getOpt("range_max")?.asFloat }.filterIndexed { i, fl -> i % 50 < 10 }
        val tfinal = final.time
        result.assertCompleted()
//        Assert.assertTrue("tfinal: $tfinal > 34", tfinal > 34f)
        return tfinal
    }

    @Test
    fun testAccRange() {
        val mins = ArrayList<Float>()
        val maxs = ArrayList<Float>()
        for (i in 0.rangeTo(40*40)) {
            val speed = 1.0f * i / 40f
            val range = Algorithm.accelerationRange(speed)
            mins.add(range.min)
            maxs.add(range.max)
        }

        maxs.dropLast(1).forEachIndexed { i, fl ->
            assert(fl >= 0, { "$i, ${maxs.subList(Math.max(0, i - 5), Math.min(i + 5, maxs.size - 1))}" })
            Assert.assertTrue("maxs $i", fl >= maxs[i+1])
        }
        mins.dropLast(1).forEachIndexed { i, fl ->
            assert(fl <= 0, { "$i, ${mins.subList(Math.max(0, i - 5), Math.min(i + 5, mins.size - 1))}" })
            Assert.assertTrue("mins $i", fl >= mins[i+1])
        }
    }
}

fun JsonObject.getOpt(key: String): JsonElement? {
    return if(this.has(key)) this.get(key) else null
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

class RandomRangeIterator(val min: Int, val max: Int): Iterator<Int> {
    val r = Random()
    override fun next(): Int {
        return r.nextInt((max - min)) + min
    }

    override fun hasNext(): Boolean {
        return true
    }

}