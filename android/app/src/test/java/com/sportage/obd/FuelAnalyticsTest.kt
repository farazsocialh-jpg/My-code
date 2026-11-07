package com.sportage.obd

import com.sportage.obd.fuel.DriveClass
import com.sportage.obd.fuel.FuelFillEntry
import com.sportage.obd.fuel.FuelMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelAnalyticsTest {

    @Test
    fun mafBasedRateFollowsStandardFormula() {
        // 10 g/s MAF -> L/h = 10 * 3.6 / (14.7 * 0.745)
        val rate = FuelMath.litersPerHourFromMaf(10.0)
        assertEquals(3.29, rate, 0.01)
    }

    @Test
    fun economyRequiresMeaningfulSpeed() {
        assertNull(FuelMath.l100From(3.0, 0.0))
        assertNull(FuelMath.l100From(3.0, 2.0))
        assertEquals(50.0, FuelMath.l100From(6.0, 12.0)!!, 0.001)
    }

    @Test
    fun kmPerLIsTheInverseOfL100() {
        assertEquals(20.0, FuelMath.kmPerL(6.0, 120.0)!!, 0.001)
        assertNull(FuelMath.kmPerL(0.0, 50.0))
    }

    @Test
    fun driveClassSplit() {
        assertEquals(DriveClass.IDLE, FuelMath.classify(0.0))
        assertEquals(DriveClass.IDLE, FuelMath.classify(2.9))
        assertEquals(DriveClass.CITY, FuelMath.classify(3.0))
        assertEquals(DriveClass.CITY, FuelMath.classify(79.9))
        assertEquals(DriveClass.HIGHWAY, FuelMath.classify(80.0))
    }

    @Test
    fun fillEntryComputesActualAndObdEconomy() {
        val fill = FuelFillEntry(0, 40.0, 500.0, null, 44.0, 500.0)
        assertEquals(8.0, fill.actualL100()!!, 0.001)  // 40 L / 500 km * 100
        assertEquals(8.8, fill.obdL100()!!, 0.001)     // 44 L / 500 km * 100
        assertEquals(40.0 / 44.0, fill.fillFactor()!!, 0.001)
    }

    @Test
    fun fillFactorNeedsEnoughData() {
        assertNull(FuelFillEntry(0, 0.4, 500.0, null, 44.0, 500.0).fillFactor())   // tiny fill
        assertNull(FuelFillEntry(0, 40.0, 500.0, null, 0.5, 500.0).fillFactor())   // no OBD data
        assertNull(FuelFillEntry(0, 40.0, 0.5, null, 44.0, 0.5).actualL100())      // no distance
    }

    @Test
    fun calibrationFactorWeightsByLitersFilled() {
        val fills = listOf(
            FuelFillEntry(0, 10.0, 100.0, null, 10.0, 100.0),   // factor 1.0
            FuelFillEntry(1, 30.0, 300.0, null, 33.0, 300.0)    // factor ~0.909
        )
        val factor = FuelMath.calibrationFactor(fills)!!
        // Weighted: (1.0*10 + 0.9090909*30) / 40
        assertEquals(0.9318, factor, 0.001)
    }

    @Test
    fun calibrationFactorIsNullWithoutUsableFills() {
        assertNull(FuelMath.calibrationFactor(emptyList()))
        assertNull(FuelMath.calibrationFactor(listOf(FuelFillEntry(0, 40.0, 500.0, null, 0.0, 500.0))))
    }

    @Test
    fun calibrationFactorIsClamped() {
        // OBD claims 1 L for a 50 L fill -> raw factor 50, clamped to 2.5.
        val fills = listOf(FuelFillEntry(0, 50.0, 500.0, null, 1.0, 500.0))
        assertEquals(2.5, FuelMath.calibrationFactor(fills)!!, 0.001)
    }
}
