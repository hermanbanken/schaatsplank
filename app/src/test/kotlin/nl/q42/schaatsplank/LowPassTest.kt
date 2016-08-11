package nl.q42.schaatsplank

import nl.q42.schaatsplank.lowpass
import nl.q42.schaatsplank.string
import org.junit.Assert
import org.junit.Test
import rx.Observable
import rx.observers.TestObserver
import rx.observers.TestSubscriber
import rx.schedulers.TestScheduler

/**
 * @author Herman Banken, Q42
 */
class LowPassTest {
    @Test
    fun testStable() {
        val input = Observable.from(arrayOf(
                floatArrayOf(1f),
                floatArrayOf(1f),
                floatArrayOf(1f),
                floatArrayOf(1f),
                floatArrayOf(1f)
        )).lowpass(1f/4f)

        val subscriber = TestSubscriber<FloatArray>()
        input.subscribe(subscriber)
        equals(arrayOf(
                floatArrayOf(1f),
                floatArrayOf(1f)
        ), subscriber.onNextEvents.toTypedArray())
    }

    @Test
    fun testStabilizing() {
        val input = Observable.from(arrayOf(
                floatArrayOf(1f),
                floatArrayOf(1f),
                floatArrayOf(1f),
                floatArrayOf(1f),
                floatArrayOf(3f),
                floatArrayOf(1f)
        )).lowpass(1f/4f)

        val subscriber = TestSubscriber<FloatArray>()
        input.subscribe(subscriber)

        equals(arrayOf(
                floatArrayOf(1f),
                floatArrayOf(1f/4*3+3f/4),
                floatArrayOf((1f/4*3+3f/4)/4*3 + 1f/4)
        ), subscriber.onNextEvents.toTypedArray())
    }

    fun floatArrayOf(vararg elements: Float): FloatArray {
        return elements
    }

    fun equals(expected: Array<FloatArray>, actual: Array<FloatArray>) {
        try {
            Assert.assertArrayEquals(expected, actual)
        } catch(e: AssertionError) {
            Assert.assertEquals(
                expected.map { it.string() }.joinToString(",\n"),
                actual.map { it.string() }.joinToString(",\n")
            )
        }
    }
}
