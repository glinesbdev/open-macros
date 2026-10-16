package dev.glinesb.openmacros.data

import dev.glinesb.openmacros.data.db.ExerciseEntity
import dev.glinesb.openmacros.data.db.RoutineDayEntity
import dev.glinesb.openmacros.data.db.WorkoutSetEntity
import dev.glinesb.openmacros.domain.WeightUnit
import dev.glinesb.openmacros.domain.training.Muscle
import dev.glinesb.openmacros.domain.training.SeedExercise
import dev.glinesb.openmacros.domain.training.SetType
import kotlinx.serialization.Serializable

/** Editable routine held in memory (and in saved state) until the user taps Save. */
@Serializable
data class RoutineDraft(val id: Long = 0, val name: String = "", val notes: String = "", val days: List<DayDraft> = emptyList())

@Serializable
data class DayDraft(val key: Long, val name: String, val exercises: List<ExerciseDraft> = emptyList())

@Serializable
data class ExerciseDraft(
    val key: Long,
    val exerciseId: Long,
    val name: String,
    val type: String,
    val sets: Int = 3,
    val repMin: Int = 8,
    val repMax: Int = 12,
    val restSeconds: Int = 90,
    val notes: String = "",
)

/** Pure helpers behind the training repository (JVM-testable). */
object TrainingPlanner {
    private val keys = java.util.concurrent.atomic.AtomicLong(0)

    /** Key for a new draft item: negative so it can never collide with a database id. */
    fun newKey(): Long = -(System.currentTimeMillis() * 1000 + (keys.incrementAndGet() % 1000))

    fun seedEntity(s: SeedExercise) = ExerciseEntity(
        slug = s.slug, name = s.name, primaryMuscles = Muscle.toCsv(s.primary), secondaryMuscles = Muscle.toCsv(s.secondary),
        equipment = s.equipment.name, type = s.type.name, isCustom = false,
    )

    /** Moves the element at [index] by [delta] (−1 up, +1 down); out-of-range moves are no-ops. */
    fun <T> move(list: List<T>, index: Int, delta: Int): List<T> {
        val to = index + delta
        if (index !in list.indices || to !in list.indices) return list
        return list.toMutableList().apply { add(to, removeAt(index)) }
    }

    /** The day after the last one trained in this routine (wrapping), or the first day. */
    fun nextDay(days: List<RoutineDayEntity>, lastDayId: Long?): RoutineDayEntity? {
        if (days.isEmpty()) return null
        val i = days.indexOfFirst { it.id == lastDayId }
        return if (i < 0) days.first() else days[(i + 1) % days.size]
    }

    /**
     * Sets to pre-fill for an exercise: the last session's warm-ups, then [targetWorking] working sets
     * copied from last time (repeating the final one if more are needed). Nothing is marked done.
     * With no history, [targetWorking] blank working sets in [unit].
     */
    fun prefill(last: List<WorkoutSetEntity>, targetWorking: Int?, unit: WeightUnit, weId: Long): List<WorkoutSetEntity> {
        val warm = last.filter { it.type == SetType.WARMUP }
        val work = last.filter { it.type == SetType.WORKING && (it.reps != null || it.weightKg != null) }
        val count = (targetWorking ?: work.size).coerceAtLeast(1)
        val working = if (work.isEmpty()) List(count) { WorkoutSetEntity(workoutExerciseId = weId, position = 0, unit = unit.name) }
        else List(count) { i -> work[minOf(i, work.lastIndex)] }
        return (warm + working).mapIndexed { i, s ->
            WorkoutSetEntity(workoutExerciseId = weId, position = i, setType = s.setType, reps = s.reps, weightKg = s.weightKg, unit = unit.name, done = false)
        }
    }
}
