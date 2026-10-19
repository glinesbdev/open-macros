package dev.glinesb.openmacros.domain.training

import dev.glinesb.openmacros.domain.DatedValue
import dev.glinesb.openmacros.domain.Units
import dev.glinesb.openmacros.domain.WeightUnit
import kotlin.math.max
import kotlin.math.roundToLong

/** A set as the progress maths sees it. Weight is canonical kg; null reps or weight means "not entered". */
data class LoggedSet(val reps: Int?, val weightKg: Double?, val type: SetType = SetType.WORKING, val done: Boolean = true) {
    val isWorking: Boolean get() = type == SetType.WORKING && done && (reps ?: 0) > 0
}

/** All logged sets of one exercise in one finished workout. */
data class Session(val workoutId: Long, val epochDay: Long, val sets: List<LoggedSet>) {
    val working: List<LoggedSet> get() = sets.filter { it.isWorking }
}

data class Record(val value: Double, val epochDay: Long, val detail: String = "")

data class PersonalRecords(
    val bestE1rm: Record?,
    val heaviest: Record?,
    val mostReps: Record?,
    val bestVolume: Record?,
)

object Strength {
    /**
     * Estimated one-rep max by Epley: w × (1 + reps / 30). A single is its own 1RM. Reliable up to
     * roughly 10–12 reps; beyond that it's only a rough guide.
     */
    fun e1rm(weight: Double, reps: Int): Double = when {
        reps <= 0 || weight <= 0.0 -> 0.0
        reps == 1 -> weight
        else -> weight * (1.0 + reps / 30.0)
    }

    /** Best e1RM per session, in kg (sessions without loaded working sets are skipped). */
    fun e1rmSeries(sessions: List<Session>): List<DatedValue> = sessions
        .mapNotNull { s -> s.working.maxOfOrNull { e1rm(it.weightKg ?: 0.0, it.reps ?: 0) }?.takeIf { it > 0 }?.let { s.epochDay to it } }
        .groupBy({ it.first }, { it.second }).map { (day, v) -> DatedValue(day, v.max()) }.sortedBy { it.epochDay }

    /** Most reps in a working set per session (used for unloaded bodyweight exercises). */
    fun bestRepsSeries(sessions: List<Session>): List<DatedValue> = sessions
        .mapNotNull { s -> s.working.maxOfOrNull { it.reps ?: 0 }?.takeIf { it > 0 }?.let { DatedValue(s.epochDay, it.toDouble()) } }
        .groupBy { it.epochDay }.map { (d, v) -> DatedValue(d, v.maxOf { it.value }) }.sortedBy { it.epochDay }

    /** Body weight for a day: the latest weigh-in on or before it, else the earliest one after. Null with no weigh-ins. */
    fun bodyweightOn(day: Long, weighIns: List<DatedValue>): Double? =
        weighIns.filter { it.epochDay <= day }.maxByOrNull { it.epochDay }?.value
            ?: weighIns.minByOrNull { it.epochDay }?.value

    /**
     * For bodyweight exercises, where a set's weightKg is the added (+) or assisted (−) load: replaces it
     * with body weight + added, so e1RM, volume and PRs reflect the real load. Without a body weight the
     * load is unknown, so weights become null and only rep-based numbers remain.
     */
    fun withBodyweight(sessions: List<Session>, weighIns: List<DatedValue>): List<Session> = sessions.map { s ->
        val bw = bodyweightOn(s.epochDay, weighIns)
        s.copy(sets = s.sets.map { set -> set.copy(weightKg = bw?.let { (it + (set.weightKg ?: 0.0)).takeIf { kg -> kg > 0 } }) })
    }

    fun volumeKg(sets: List<LoggedSet>): Double = sets.filter { it.isWorking }.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }

    /** PRs over working sets only (warm-ups never count). Ties keep the earliest date. */
    fun records(sessions: List<Session>): PersonalRecords {
        var e1: Record? = null; var heavy: Record? = null; var reps: Record? = null; var vol: Record? = null
        for (s in sessions.sortedBy { it.epochDay }) {
            for (set in s.working) {
                val w = set.weightKg ?: 0.0; val r = set.reps ?: 0
                val est = e1rm(w, r)
                if (est > 0 && (e1 == null || est > e1.value + 1e-9)) e1 = Record(est, s.epochDay, "${r} reps")
                if (w > 0 && (heavy == null || w > heavy.value + 1e-9)) heavy = Record(w, s.epochDay, "× $r")
                if (r > 0 && (reps == null || r > reps.value)) reps = Record(r.toDouble(), s.epochDay, if (w > 0) "@ weight" else "")
            }
            val v = volumeKg(s.sets)
            if (v > 0 && (vol == null || v > vol.value + 1e-9)) vol = Record(v, s.epochDay)
        }
        return PersonalRecords(e1, heavy, reps, vol)
    }
}

enum class ProgressionAction { INCREASE_WEIGHT, ADD_LOAD, ADD_REPS, DELOAD }

/** A suggestion for the next session; [weightKg] is null when the weight should stay the same or there's no load. */
data class ProgressionSuggestion(
    val action: ProgressionAction,
    val weightKg: Double?,
    val targetReps: Int,
    val message: String,
)

/**
 * Double progression: work inside a rep range at one weight; once every working set reaches the top
 * of the range, add weight and start again from the bottom. Compounds jump 5 lb / 2.5 kg, isolations
 * 2.5 lb / 1 kg. If every set falls short of the bottom of the range two sessions running, back off ~10%.
 */
object Progression {
    fun increment(type: ExerciseType, unit: WeightUnit): Double = when (type) {
        ExerciseType.COMPOUND -> if (unit == WeightUnit.LB) 5.0 else 2.5
        else -> if (unit == WeightUnit.LB) 2.5 else 1.0
    }

    fun suggest(
        last: List<LoggedSet>,
        repMin: Int,
        repMax: Int,
        type: ExerciseType,
        unit: WeightUnit,
        previous: List<LoggedSet>? = null,
        /** Bodyweight exercise: weights are added (+) / assisted (−) load and "no weight" means plain BW. */
        bodyweight: Boolean = false,
    ): ProgressionSuggestion? {
        if (type == ExerciseType.CARDIO) return null
        val working = last.filter { it.isWorking }
        if (working.isEmpty() || repMin <= 0 || repMax < repMin) return null
        val topKg = working.maxOf { it.weightKg ?: 0.0 }
        val topDisplay = Units.toDisplayWeight(topKg, unit)
        val inc = increment(type, unit)
        val reps = working.map { it.reps ?: 0 }

        if (bodyweight) return suggestBodyweight(working, reps, repMin, repMax, inc, unit, previous)
        if (reps.all { it >= repMax }) {
            if (topKg <= 0.0) return ProgressionSuggestion(
                ProgressionAction.ADD_LOAD, null, repMin,
                "Every set hit $repMax. Add some load or switch to a harder variation, and work back up from $repMin.",
            )
            val next = topDisplay + inc
            return ProgressionSuggestion(
                ProgressionAction.INCREASE_WEIGHT, Units.fromDisplayWeight(next, unit), repMin,
                "Every set hit $repMax. Go up to ${fmt(next)} ${unit.symbol} and aim for $repMin+ reps.",
            )
        }
        val prevWorking = previous?.filter { it.isWorking }.orEmpty()
        val missedTwice = reps.all { it < repMin } && prevWorking.isNotEmpty() &&
            prevWorking.all { (it.reps ?: 0) < repMin } && prevWorking.maxOf { it.weightKg ?: 0.0 } <= topKg + 1e-6
        if (missedTwice && topKg > 0.0) {
            val next = max(inc, roundTo(topDisplay * 0.9, inc))
            return ProgressionSuggestion(
                ProgressionAction.DELOAD, Units.fromDisplayWeight(next, unit), repMax,
                "Below $repMin reps two sessions running. Drop to ${fmt(next)} ${unit.symbol} and build back up.",
            )
        }
        val target = (reps.minOrNull() ?: repMin).coerceAtLeast(repMin - 1) + 1
        return ProgressionSuggestion(
            ProgressionAction.ADD_REPS, null, target.coerceAtMost(repMax),
            if (topKg > 0) "Stay at ${fmt(topDisplay)} ${unit.symbol} and add reps until every set hits $repMax."
            else "Add reps until every set hits $repMax.",
        )
    }

    private fun suggestBodyweight(
        working: List<LoggedSet>, reps: List<Int>, repMin: Int, repMax: Int, inc: Double, unit: WeightUnit, previous: List<LoggedSet>?,
    ): ProgressionSuggestion {
        val added = Units.toDisplayWeight(working.maxOf { it.weightKg ?: 0.0 }, unit)
        if (reps.all { it >= repMax }) {
            val next = added + inc
            return ProgressionSuggestion(
                ProgressionAction.INCREASE_WEIGHT, Units.fromDisplayWeight(next, unit), repMin,
                "Every set hit $repMax. Go to ${bw(next, unit)} (${if (added < 0) "less assistance" else "add weight"}) and aim for $repMin+ reps.",
            )
        }
        val prevWorking = previous?.filter { it.isWorking }.orEmpty()
        if (reps.all { it < repMin } && prevWorking.isNotEmpty() && prevWorking.all { (it.reps ?: 0) < repMin }) {
            val next = added - inc
            return ProgressionSuggestion(
                ProgressionAction.DELOAD, Units.fromDisplayWeight(next, unit), repMax,
                "Below $repMin reps two sessions running. Try ${bw(next, unit)} and build back up.",
            )
        }
        val target = (reps.minOrNull() ?: repMin).coerceAtLeast(repMin - 1) + 1
        return ProgressionSuggestion(ProgressionAction.ADD_REPS, null, target.coerceAtMost(repMax),
            "Stay at ${bw(added, unit)} and add reps until every set hits $repMax.")
    }

    /** "BW", "BW + 25 lb" or "BW − 20 lb" from a display-unit added load. */
    fun bw(addedDisplay: Double, unit: WeightUnit): String = when {
        addedDisplay > 0.004 -> "BW + ${fmt(addedDisplay)} ${unit.symbol}"
        addedDisplay < -0.004 -> "BW − ${fmt(-addedDisplay)} ${unit.symbol}"
        else -> "BW"
    }

    fun roundTo(value: Double, step: Double): Double = (value / step).roundToLong() * step

    private fun fmt(d: Double): String {
        val s = "%.2f".format(java.util.Locale.ROOT, d)
        return s.trimEnd('0').trimEnd('.')
    }
}
