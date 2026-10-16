package dev.glinesb.openmacros.training

import dev.glinesb.openmacros.domain.Units
import dev.glinesb.openmacros.domain.WeightUnit
import dev.glinesb.openmacros.domain.training.LoggedSet
import dev.glinesb.openmacros.domain.training.Session
import dev.glinesb.openmacros.domain.training.SetType
import dev.glinesb.openmacros.domain.training.Strength
import dev.glinesb.openmacros.ui.training.weightText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StrengthTest {
    @Test fun epley() {
        assertEquals(100.0 * (1 + 5 / 30.0), Strength.e1rm(100.0, 5), 1e-9) // 116.67
        assertEquals(116.667, Strength.e1rm(100.0, 5), 1e-3)
        assertEquals(140.0, Strength.e1rm(140.0, 1), 1e-9) // a single is its own 1RM
        assertEquals(133.333, Strength.e1rm(100.0, 10), 1e-3)
        assertEquals(0.0, Strength.e1rm(100.0, 0), 0.0)
        assertEquals(0.0, Strength.e1rm(0.0, 10), 0.0)
    }

    @Test fun unitConversionRoundTripsForDisplay() {
        // Canonical kg in storage, the entered unit for display: 135 lb must come back as exactly "135".
        for (lb in listOf(45.0, 135.0, 225.0, 315.0, 137.5, 2.5)) {
            val kg = Units.fromDisplayWeight(lb, WeightUnit.LB)
            assertEquals(lb, Units.toDisplayWeight(kg, WeightUnit.LB), 1e-9)
            assertEquals(lb.toBigDecimal().stripTrailingZeros().toPlainString(), weightText(kg, WeightUnit.LB))
        }
        assertEquals(61.235, Units.fromDisplayWeight(135.0, WeightUnit.LB), 1e-3)
        assertEquals("100", weightText(100.0, WeightUnit.KG))
        assertEquals("220.46", weightText(100.0, WeightUnit.LB))
        assertEquals("", weightText(null, WeightUnit.KG))
        // Switching an exercise's display unit doesn't change the stored kg.
        val kg = Units.fromDisplayWeight(20.0, WeightUnit.KG)
        assertEquals("44.09", weightText(kg, WeightUnit.LB))
    }

    @Test fun recordsIgnoreWarmupsAndUndoneSets() {
        val s1 = Session(1, 100, listOf(
            LoggedSet(5, 140.0, SetType.WARMUP), // heavier warm-up never counts
            LoggedSet(8, 100.0), LoggedSet(8, 100.0),
            LoggedSet(3, 150.0, SetType.WORKING, done = false),
        ))
        val s2 = Session(2, 107, listOf(LoggedSet(5, 110.0), LoggedSet(12, 80.0)))
        val pr = Strength.records(listOf(s2, s1))
        assertEquals(Strength.e1rm(110.0, 5), pr.bestE1rm!!.value, 1e-9) // 128.3 > 126.7 (100×8) > 112 (80×12)
        assertEquals(107L, pr.bestE1rm!!.epochDay)
        assertEquals(110.0, pr.heaviest!!.value, 1e-9)
        assertEquals(12.0, pr.mostReps!!.value, 1e-9)
        assertEquals(1600.0, pr.bestVolume!!.value, 1e-9) // s1: 800+800 vs s2: 550+960 = 1510
        assertEquals(100L, pr.bestVolume!!.epochDay)
    }

    @Test fun seriesTakesBestSetPerSessionAndSkipsUnloaded() {
        val series = Strength.e1rmSeries(listOf(
            Session(1, 10, listOf(LoggedSet(5, 100.0), LoggedSet(10, 90.0))),
            Session(2, 12, listOf(LoggedSet(10, null))),
        ))
        assertEquals(1, series.size)
        assertEquals(Strength.e1rm(90.0, 10), series.single().value, 1e-9) // 90×10 (120) beats 100×5 (116.7)
    }

    @Test fun emptyRecords() {
        val pr = Strength.records(emptyList())
        assertNull(pr.bestE1rm); assertNull(pr.heaviest); assertNull(pr.mostReps); assertNull(pr.bestVolume)
    }
}
