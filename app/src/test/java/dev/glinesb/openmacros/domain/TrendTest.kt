package dev.glinesb.openmacros.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendTest {
    private fun pts(vararg p: Pair<Long, Double>) = p.map { DatedValue(it.first, it.second) }

    @Test fun movingAverageOfEmptyIsEmpty() {
        assertTrue(Trend.trailingMovingAverage(emptyList()).isEmpty())
        assertTrue(Trend.exponentialTrend(emptyList()).isEmpty())
    }

    @Test fun trailingAverageUsesCalendarWindow() {
        val out = Trend.trailingMovingAverage(pts(0L to 10.0, 1L to 20.0, 2L to 30.0, 3L to 40.0), windowDays = 3)
        assertEquals(listOf(10.0, 15.0, 20.0, 30.0), out.map { it.value })
        assertEquals(listOf(0L, 1L, 2L, 3L), out.map { it.epochDay })
    }

    @Test fun trailingAverageSkipsGapsByDateNotCount() {
        // Day 10 is more than 7 days after days 0-1, so they drop out of its window.
        val out = Trend.trailingMovingAverage(pts(0L to 100.0, 1L to 102.0, 10L to 90.0), windowDays = 7)
        assertEquals(101.0, out[1].value, 1e-9)
        assertEquals(90.0, out[2].value, 1e-9)
    }

    @Test fun trailingAverageSortsInput() {
        val out = Trend.trailingMovingAverage(pts(2L to 30.0, 0L to 10.0, 1L to 20.0), windowDays = 7)
        assertEquals(listOf(10.0, 15.0, 20.0), out.map { it.value })
    }

    @Test fun windowOfOneIsIdentity() {
        val input = pts(0L to 1.0, 1L to 5.0, 2L to 3.0)
        assertEquals(input, Trend.trailingMovingAverage(input, windowDays = 1))
    }

    @Test fun exponentialTrendSeedsWithFirstAndSmooths() {
        val out = Trend.exponentialTrend(pts(0L to 100.0, 1L to 110.0), alphaPerDay = 0.1)
        assertEquals(100.0, out[0].value, 1e-9)
        assertEquals(101.0, out[1].value, 1e-9)
    }

    @Test fun exponentialTrendIsTimeAware() {
        // A 3-day gap applies 1 - 0.9^3 = 0.271 of the difference, same as 3 daily steps at the new value.
        val gap = Trend.exponentialTrend(pts(0L to 100.0, 3L to 110.0), 0.1).last().value
        val daily = Trend.exponentialTrend(pts(0L to 100.0, 1L to 110.0, 2L to 110.0, 3L to 110.0), 0.1).last().value
        assertEquals(102.71, gap, 1e-9)
        assertEquals(daily, gap, 1e-9)
    }

    @Test fun constantSeriesStaysConstant() {
        val out = Trend.exponentialTrend((0L..20L).map { DatedValue(it, 80.0) })
        out.forEach { assertEquals(80.0, it.value, 1e-9) }
    }

    @Test fun trendLagsBehindNoisyDrop() {
        val values = listOf(80.0, 81.0, 79.5, 80.5, 78.0)
        val out = Trend.exponentialTrend(values.mapIndexed { i, v -> DatedValue(i.toLong(), v) })
        val last = out.last().value
        assertTrue(last < 80.5 && last > 78.0)
    }
}
