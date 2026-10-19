package dev.glinesb.openmacros.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.glinesb.openmacros.domain.WeightUnit
import dev.glinesb.openmacros.domain.training.ExerciseType
import dev.glinesb.openmacros.domain.training.Equipment
import dev.glinesb.openmacros.domain.training.LoggedSet
import dev.glinesb.openmacros.domain.training.Muscle
import dev.glinesb.openmacros.domain.training.SetType
import kotlinx.serialization.Serializable

/**
 * An exercise in the library. Built-in ones have the seed [slug] and isCustom = false; the user's own
 * have a generated "custom-…" slug. Muscles are comma-separated [Muscle] names.
 */
@Serializable
@Entity(tableName = "exercises", indices = [Index(value = ["slug"], unique = true), Index("name")])
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slug: String,
    val name: String,
    val primaryMuscles: String,
    val secondaryMuscles: String = "",
    /** [Equipment] name. */
    val equipment: String,
    /** [ExerciseType] name. */
    val type: String,
    val isCustom: Boolean = false,
) {
    val exerciseType: ExerciseType get() = runCatching { ExerciseType.valueOf(type) }.getOrDefault(ExerciseType.COMPOUND)
    val equipmentType: Equipment get() = runCatching { Equipment.valueOf(equipment) }.getOrDefault(Equipment.OTHER)
    val isBodyweight: Boolean get() = dev.glinesb.openmacros.domain.training.isBodyweightLoad(equipmentType, exerciseType, slug)
    val primary: List<Muscle> get() = Muscle.parseList(primaryMuscles)
    val secondary: List<Muscle> get() = Muscle.parseList(secondaryMuscles)
}

@Serializable
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val notes: String = "",
    /** Order in the routines list. */
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "routine_days",
    foreignKeys = [ForeignKey(entity = RoutineEntity::class, parentColumns = ["id"], childColumns = ["routineId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("routineId")],
)
data class RoutineDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val name: String,
    val position: Int,
)

@Serializable
@Entity(
    tableName = "routine_exercises",
    foreignKeys = [
        ForeignKey(entity = RoutineDayEntity::class, parentColumns = ["id"], childColumns = ["dayId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("dayId"), Index("exerciseId")],
)
data class RoutineExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val exerciseId: Long,
    val position: Int,
    val targetSets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
    val notes: String = "",
)

/** A workout. [finishedAt] is null while it's in progress (at most one at a time). */
@Serializable
@Entity(tableName = "workouts", indices = [Index("epochDay"), Index("finishedAt")])
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val epochDay: Long,
    val startedAt: Long,
    val finishedAt: Long? = null,
    /** Where it came from, if a routine day; kept as plain ids (no FK) so deleting a routine keeps history. */
    val routineId: Long? = null,
    val routineDayId: Long? = null,
    val notes: String = "",
)

/**
 * An exercise within a workout. The name and type are snapshotted so history survives deleting a
 * custom exercise (the link is then cleared).
 */
@Serializable
@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(entity = WorkoutEntity::class, parentColumns = ["id"], childColumns = ["workoutId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("workoutId"), Index("exerciseId")],
)
data class WorkoutExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long?,
    val name: String,
    /** [ExerciseType] name at log time. */
    val type: String,
    val position: Int,
    /** Display unit for this exercise in this workout ([WeightUnit] name). */
    val unit: String,
    val repMin: Int? = null,
    val repMax: Int? = null,
    val restSeconds: Int = 90,
    val notes: String = "",
    /** Bodyweight exercise (snapshotted): set weights are added (+) / assisted (−) load, null = plain BW. */
    @ColumnInfo(defaultValue = "0") val isBodyweight: Boolean = false,
) {
    val weightUnit: WeightUnit get() = runCatching { WeightUnit.valueOf(unit) }.getOrDefault(WeightUnit.LB)
    val exerciseType: ExerciseType get() = runCatching { ExerciseType.valueOf(type) }.getOrDefault(ExerciseType.COMPOUND)
}

/**
 * One set. Weight is canonical kg; [unit] is the unit it was entered in, used for display. For cardio, [reps]
 * is minutes. For bodyweight exercises, weight is the added (+) or assisted (−) load and null means plain BW.
 */
@Serializable
@Entity(
    tableName = "workout_sets",
    foreignKeys = [ForeignKey(entity = WorkoutExerciseEntity::class, parentColumns = ["id"], childColumns = ["workoutExerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("workoutExerciseId")],
)
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutExerciseId: Long,
    val position: Int,
    /** [SetType] name. */
    val setType: String = SetType.WORKING.name,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val unit: String = WeightUnit.LB.name,
    val done: Boolean = false,
) {
    val type: SetType get() = runCatching { SetType.valueOf(setType) }.getOrDefault(SetType.WORKING)
    val weightUnit: WeightUnit get() = runCatching { WeightUnit.valueOf(unit) }.getOrDefault(WeightUnit.LB)
    fun toLogged() = LoggedSet(reps, weightKg, type, done)
}
