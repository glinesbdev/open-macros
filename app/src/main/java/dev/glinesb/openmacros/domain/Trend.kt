package dev.glinesb.openmacros.domain

import kotlin.math.pow

/** A value on a calendar day (days since 1970-01-01, i.e. LocalDate.toEpochDay()). */
data class DatedValue(val epochDay: Long, val value: Double)

object Trend {
    /**
     * Trailing simple moving average over a calendar window: for each point, the mean of all
     * points in [day − windowDays + 1, day]. Missing days are simply absent from the mean.
     * Input does not need to be sorted; output is sorted by day.
     */
    fun trailingMovingAverage(points: List<DatedValue>, windowDays: Int = 7): List<DatedValue> {
        require(windowDays >= 1) { "windowDays must be >= 1" }
        val sorted = points.sortedBy { it.epochDay }
        val out = ArrayList<DatedValue>(sorted.size)
        var start = 0
        var sum = 0.0
        for (end in sorted.indices) {
            sum += sorted[end].value
            val minDay = sorted[end].epochDay - windowDays + 1
            while (sorted[start].epochDay < minDay) {
                sum -= sorted[start].value
                start++
            }
            out += DatedValue(sorted[end].epochDay, sum / (end - start + 1))
        }
        return out
    }

    /**
     * Time-aware exponentially smoothed trend (the "Hacker's Diet" style weight trend).
     * [alphaPerDay] is the smoothing factor for one day; across a gap of g days the effective
     * factor is 1 − (1 − α)^g, so sparse logging doesn't make the trend lag or jump unfairly.
     * The first point seeds the trend. Output is sorted by day.
     */
    fun exponentialTrend(points: List<DatedValue>, alphaPerDay: Double = 0.1): List<DatedValue> {
        require(alphaPerDay > 0.0 && alphaPerDay <= 1.0) { "alphaPerDay must be in (0, 1]" }
        val sorted = points.sortedBy { it.epochDay }
        if (sorted.isEmpty()) return emptyList()
        val out = ArrayList<DatedValue>(sorted.size)
        var trend = sorted.first().value
        var lastDay = sorted.first().epochDay
        out += DatedValue(lastDay, trend)
        for (i in 1 until sorted.size) {
            val p = sorted[i]
            val gap = (p.epochDay - lastDay).coerceAtLeast(0)
            val alpha = if (gap == 0L) alphaPerDay else 1.0 - (1.0 - alphaPerDay).pow(gap.toDouble())
            trend += alpha * (p.value - trend)
            lastDay = p.epochDay
            out += DatedValue(p.epochDay, trend)
        }
        return out
    }
}
