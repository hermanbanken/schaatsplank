package nl.q42.schaatsplank

import org.junit.Test

import org.junit.Assert.*

/**
 * @author Herman Banken, Q42
 */
class GravityTest {

    @Test
    fun testTilt() {
//        assertEquals(3/4*Math.PI, Gravity(2f, -2f).tilt, 0.001)
        assertEquals(50.0, Gravity(8f, 6f).tiltAngle, 5.0)
        assertEquals(80.0, Gravity(6f, 7f).tiltAngle, 5.0)
        assertEquals(30.0, Gravity(5f, 8f).tiltAngle, 5.0)
    }
    @Test
    fun testTiltAngle() {
        val g1 = Gravity.fromAngle(45f)
        assertEquals(45.0, g1.tiltAngle, 0.001)

        val g2 = Gravity.fromAngle(90f)
        assertEquals(90.0, g2.tiltAngle, 0.001)

        val g3 = Gravity.fromAngle(30f)
        assertEquals(30.0, g3.tiltAngle, 0.001)
    }
}