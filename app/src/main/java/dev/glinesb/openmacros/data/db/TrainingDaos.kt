package dev.glinesb.openmacros.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun byId(id: Long): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observe(id: Long): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercises WHERE slug IN (:slugs)")
    suspend fun bySlugs(slugs: List<String>): List<ExerciseEntity>

    @Query("SELECT COUNT(*) FROM exercises WHERE isCustom = 0")
    suspend fun builtInCount(): Int

    /** Seeds missing built-ins; existing rows (and their ids) are left alone. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(items: List<ExerciseEntity>)

    @Insert
    suspend fun insert(item: ExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ExerciseEntity>)

    @Update
    suspend fun update(item: ExerciseEntity)

    @Delete
    suspend fun delete(item: ExerciseEntity)

    @Query("DELETE FROM exercises")
    suspend fun deleteAll()
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY position, id")
    fun observeRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines ORDER BY position, id")
    suspend fun routines(): List<RoutineEntity>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun routine(id: Long): RoutineEntity?

    @Query("SELECT * FROM routine_days ORDER BY routineId, position")
    fun observeDays(): Flow<List<RoutineDayEntity>>

    @Query("SELECT * FROM routine_days WHERE routineId = :routineId ORDER BY position")
    suspend fun days(routineId: Long): List<RoutineDayEntity>

    @Query("SELECT * FROM routine_days WHERE id = :id")
    suspend fun day(id: Long): RoutineDayEntity?

    @Query("SELECT * FROM routine_exercises WHERE dayId = :dayId ORDER BY position")
    suspend fun exercises(dayId: Long): List<RoutineExerciseEntity>

    @Query("SELECT re.* FROM routine_exercises re JOIN routine_days d ON d.id = re.dayId WHERE d.routineId = :routineId ORDER BY d.position, re.position")
    suspend fun exercisesForRoutine(routineId: Long): List<RoutineExerciseEntity>

    @Query("SELECT * FROM routine_days ORDER BY routineId, position")
    suspend fun allDays(): List<RoutineDayEntity>

    @Query("SELECT * FROM routine_exercises ORDER BY dayId, position")
    suspend fun allExercises(): List<RoutineExerciseEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM routines")
    suspend fun maxPosition(): Int

    @Insert
    suspend fun insertRoutine(r: RoutineEntity): Long

    @Update
    suspend fun updateRoutine(r: RoutineEntity)

    @Update
    suspend fun updateRoutines(r: List<RoutineEntity>)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteRoutine(id: Long)

    @Insert
    suspend fun insertDay(d: RoutineDayEntity): Long

    @Update
    suspend fun updateDay(d: RoutineDayEntity)

    @Query("DELETE FROM routine_days WHERE id = :id")
    suspend fun deleteDay(id: Long)

    @Query("DELETE FROM routine_exercises WHERE dayId = :dayId")
    suspend fun deleteExercisesForDay(dayId: Long)

    @Insert
    suspend fun insertExercises(e: List<RoutineExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllRoutines(r: List<RoutineEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllDays(d: List<RoutineDayEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllExercises(e: List<RoutineExerciseEntity>)

    @Query("DELETE FROM routines")
    suspend fun deleteAll()
}

/** A set joined with its workout, for history, charts and CSV. */
data class SetRow(
    val workoutId: Long,
    val workoutName: String,
    val epochDay: Long,
    val startedAt: Long,
    val workoutExerciseId: Long,
    val exerciseId: Long?,
    val exerciseName: String,
    val exerciseType: String,
    val exercisePosition: Int,
    val setId: Long,
    val setPosition: Int,
    val setType: String,
    val reps: Int?,
    val weightKg: Double?,
    val unit: String,
    val done: Boolean,
)

/** Summary line for the history list. */
data class WorkoutSummary(
    val id: Long,
    val name: String,
    val epochDay: Long,
    val startedAt: Long,
    val finishedAt: Long?,
    val exerciseCount: Int,
    val setCount: Int,
    val volumeKg: Double,
)

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workouts WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun active(): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE id = :id")
    fun observe(id: Long): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun byId(id: Long): WorkoutEntity?

    @Query(
        "SELECT w.id, w.name, w.epochDay, w.startedAt, w.finishedAt, " +
            "(SELECT COUNT(*) FROM workout_exercises we WHERE we.workoutId = w.id) AS exerciseCount, " +
            "(SELECT COUNT(*) FROM workout_sets s JOIN workout_exercises we ON we.id = s.workoutExerciseId WHERE we.workoutId = w.id AND s.setType = 'WORKING') AS setCount, " +
            "(SELECT COALESCE(SUM(COALESCE(s.weightKg, 0) * COALESCE(s.reps, 0)), 0) FROM workout_sets s JOIN workout_exercises we ON we.id = s.workoutExerciseId " +
            "WHERE we.workoutId = w.id AND s.setType = 'WORKING' AND we.type != 'CARDIO') AS volumeKg " +
            "FROM workouts w WHERE w.finishedAt IS NOT NULL ORDER BY w.startedAt DESC",
    )
    fun observeHistory(): Flow<List<WorkoutSummary>>

    @Query("SELECT * FROM workouts WHERE finishedAt IS NOT NULL AND routineId = :routineId ORDER BY startedAt DESC LIMIT 1")
    suspend fun lastForRoutine(routineId: Long): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE finishedAt IS NOT NULL AND routineId = :routineId ORDER BY startedAt DESC LIMIT 1")
    fun observeLastForRoutine(routineId: Long): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workout_exercises WHERE workoutId = :workoutId ORDER BY position")
    fun observeExercises(workoutId: Long): Flow<List<WorkoutExerciseEntity>>

    @Query("SELECT * FROM workout_exercises WHERE workoutId = :workoutId ORDER BY position")
    suspend fun exercises(workoutId: Long): List<WorkoutExerciseEntity>

    @Query("SELECT s.* FROM workout_sets s JOIN workout_exercises we ON we.id = s.workoutExerciseId WHERE we.workoutId = :workoutId ORDER BY we.position, s.position")
    fun observeSets(workoutId: Long): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_sets WHERE workoutExerciseId = :weId ORDER BY position")
    suspend fun sets(weId: Long): List<WorkoutSetEntity>

    /** Every set of an exercise across finished workouts, oldest first. */
    @Query(
        "SELECT w.id AS workoutId, w.name AS workoutName, w.epochDay, w.startedAt, we.id AS workoutExerciseId, we.exerciseId, " +
            "we.name AS exerciseName, we.type AS exerciseType, we.position AS exercisePosition, s.id AS setId, s.position AS setPosition, " +
            "s.setType, s.reps, s.weightKg, s.unit, s.done " +
            "FROM workout_sets s JOIN workout_exercises we ON we.id = s.workoutExerciseId JOIN workouts w ON w.id = we.workoutId " +
            "WHERE we.exerciseId = :exerciseId AND w.finishedAt IS NOT NULL ORDER BY w.startedAt, s.position",
    )
    fun observeExerciseSets(exerciseId: Long): Flow<List<SetRow>>

    @Query(
        "SELECT w.id AS workoutId, w.name AS workoutName, w.epochDay, w.startedAt, we.id AS workoutExerciseId, we.exerciseId, " +
            "we.name AS exerciseName, we.type AS exerciseType, we.position AS exercisePosition, s.id AS setId, s.position AS setPosition, " +
            "s.setType, s.reps, s.weightKg, s.unit, s.done " +
            "FROM workout_sets s JOIN workout_exercises we ON we.id = s.workoutExerciseId JOIN workouts w ON w.id = we.workoutId " +
            "WHERE w.finishedAt IS NOT NULL ORDER BY w.startedAt, we.position, s.position",
    )
    suspend fun allFinishedSets(): List<SetRow>

    /** The most recent finished occurrences of an exercise (newest first). */
    @Query(
        "SELECT we.* FROM workout_exercises we JOIN workouts w ON w.id = we.workoutId " +
            "WHERE we.exerciseId = :exerciseId AND w.finishedAt IS NOT NULL ORDER BY w.startedAt DESC LIMIT :limit",
    )
    suspend fun recentOccurrences(exerciseId: Long, limit: Int = 2): List<WorkoutExerciseEntity>

    @Insert
    suspend fun insertWorkout(w: WorkoutEntity): Long

    @Update
    suspend fun updateWorkout(w: WorkoutEntity)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkout(id: Long)

    @Insert
    suspend fun insertExercise(e: WorkoutExerciseEntity): Long

    @Update
    suspend fun updateExercise(e: WorkoutExerciseEntity)

    @Update
    suspend fun updateExercises(e: List<WorkoutExerciseEntity>)

    @Query("DELETE FROM workout_exercises WHERE id = :id")
    suspend fun deleteExercise(id: Long)

    @Insert
    suspend fun insertSet(s: WorkoutSetEntity): Long

    @Insert
    suspend fun insertSets(s: List<WorkoutSetEntity>)

    @Update
    suspend fun updateSet(s: WorkoutSetEntity)

    @Update
    suspend fun updateSets(s: List<WorkoutSetEntity>)

    @Query("DELETE FROM workout_sets WHERE id = :id")
    suspend fun deleteSet(id: Long)

    // Field-level updates so quick edits to different fields of one set never overwrite each other.
    @Query("UPDATE workout_sets SET reps = :reps WHERE id = :id")
    suspend fun setReps(id: Long, reps: Int?)

    @Query("UPDATE workout_sets SET weightKg = :kg WHERE id = :id")
    suspend fun setWeight(id: Long, kg: Double?)

    @Query("UPDATE workout_sets SET done = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)

    @Query("UPDATE workout_sets SET setType = :type WHERE id = :id")
    suspend fun setType(id: Long, type: String)

    @Query("SELECT * FROM workouts ORDER BY startedAt")
    suspend fun allWorkouts(): List<WorkoutEntity>

    @Query("SELECT * FROM workout_exercises ORDER BY workoutId, position")
    suspend fun allExercises(): List<WorkoutExerciseEntity>

    @Query("SELECT * FROM workout_sets ORDER BY workoutExerciseId, position")
    suspend fun allSets(): List<WorkoutSetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllWorkouts(w: List<WorkoutEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllExercises(e: List<WorkoutExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSets(s: List<WorkoutSetEntity>)

    @Query("DELETE FROM workouts")
    suspend fun deleteAll()
}
