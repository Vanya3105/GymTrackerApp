package com.example.gymtracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlateCalculatorTest {
    private val kg = defaultPlateSetup(WeightUnit.KG)
    private val lb = defaultPlateSetup(WeightUnit.LB)

    @Test
    fun exactLoads_heaviestFirst() {
        assertEquals(listOf(25f, 15f, 2.5f), calculatePlates(105f, kg).exact!!.perSide)
        assertEquals(listOf(45f, 45f), calculatePlates(225f, lb).exact!!.perSide)
        assertEquals(listOf(1.25f), calculatePlates(22.5f, kg).exact!!.perSide)
    }

    @Test
    fun barOnly_andNoBar() {
        assertEquals(emptyList<Float>(), calculatePlates(20f, kg).exact!!.perSide)
        assertEquals(listOf(10f), calculatePlates(20f, kg.copy(barWeight = 0f)).exact!!.perSide)
    }

    @Test
    fun belowTheBar_offersTheBar() {
        val math = calculatePlates(15f, kg)
        assertNull(math.exact)
        assertNull(math.lighter)
        assertEquals(20f, math.heavier!!.total)
    }

    @Test
    fun unloadable_givesNearestEitherSide() {
        val math = calculatePlates(101f, kg)
        assertNull(math.exact)
        assertEquals(100f, math.lighter!!.total)
        assertEquals(102.5f, math.heavier!!.total)
    }

    @Test
    fun findsLoadsGreedyWouldMiss() {
        val gaps = PlateSetup(20f, listOf(15f, 10f))
        assertEquals(listOf(10f, 10f), calculatePlates(60f, gaps).exact!!.perSide)
    }

    @Test
    fun noPlatesSelected_onlyTheBar() {
        val math = calculatePlates(60f, kg.copy(plates = emptyList()))
        assertNull(math.exact)
        assertEquals(20f, math.lighter!!.total)
    }
}
