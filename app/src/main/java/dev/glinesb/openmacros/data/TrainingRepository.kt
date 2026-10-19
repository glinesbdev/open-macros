package dev.glinesb.openmacros.data

import androidx.room.withTransaction
import dev.glinesb.openmacros.data.db.AppDatabase
import dev.glinesb.openmacros.data.db.ExerciseEntity
import dev.glinesb.openmacros.data.db.RoutineDayEntity
import dev.glinesb.openmacros.data.db.RoutineEntity
import dev.glinesb.openmacros.data.db.RoutineExerciseEntity
import dev.glinesb.openmacros.data.db.WorkoutEntity
import dev.glinesb.openmacros.data.db.WorkoutExerciseEntity
import dev.glinesb.openmacros.data.db.WorkoutSetEntity
import dev.glinesb.openmacros.domain.WeightUnit
import dev.glinesb.openmacros.domain.training.Equipment
import dev.glinesb.openmacros.domain.training.ExerciseLibrary
import dev.glinesb.openmacros.domain.training.ExerciseType
import dev.glinesb.openmacros.domain.training.Muscle
import dev.glinesb.openmacros.domain.training.Progression
import dev.glinesb.openmacros.domain.training.ProgressionSuggestion
import dev.glinesb.openmacros.domain.training.SplitRecommendation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.UUID

class TrainingRepository(private val db: AppDatabase, private val settings: SettingsStore) {
    private val exercises = db.exercises()
    private val routines = db.routines()
    private val workouts = db.workouts()
    private val seedLock = Mutex()

    val allExercises = exercises.observeAll()
    val allRoutines = routines.observeRoutines()
    val allDays = routines.observeDays()
    val activeWorkout = workouts.observeActive()
    val history = workouts.observeHistory()

    fun observeExercise(id: Long) = exercises.observe(id)
    fun observeExerciseSets(id: Long) = workouts.observeExerciseSets(id)
    fun observeWorkout(id: Long) = workouts.observe(id)
    fun observeWorkoutExercises(id: Long) = workouts.observeExercises(id)
    fun observeWorkoutSets(id: Long) = workouts.observeSets(id)
    fun observeLastForRoutine(routineId: Long) = workouts.observeLastForRoutine(routineId)
    suspend fun days(routineId: Long) = routines.days(routineId)

    /** Adds any built-in exercises that aren't in the database yet (first launch, upgrades, after import). */
    suspend fun ensureSeeded() = seedLock.withLock {
        if (exercises.builtInCount() < ExerciseLibrary.SEED.size) exercises.insertIgnore(ExerciseLibrary.SEED.map(TrainingPlanner::seedEntity))
        // Keep built-ins in sync with the bundled library (e.g. renamed "Dip" → "Body Dips"); ids stay the same.
        val seeds = ExerciseLibrary.bySlug
        exercises.all().filter { !it.isCustom }.forEach { e ->
            val want = seeds[e.slug]?.let(TrainingPlanner::seedEntity)?.copy(id = e.id) ?: return@forEach
            if (want != e) exercises.update(want)
        }
    }

    // ---- Exercises ----

    suspend fun createCustomExercise(name: String, primary: List<Muscle>, secondary: List<Muscle>, equipment: Equipment, type: ExerciseType): Long =
        exercises.insert(
            ExerciseEntity(
                slug = "custom-" + UUID.randomUUID().toString(), name = name.trim(), primaryMuscles = Muscle.toCsv(primary),
                secondaryMuscles = Muscle.toCsv(secondary - primary.toSet()), equipment = equipment.name, type = type.name, isCustom = true,
            ),
        )

    suspend fun updateCustomExercise(e: ExerciseEntity) { if (e.isCustom) exercises.update(e) }

    /** Removes a custom exercise from routines; logged history keeps its name but loses the link. */
    suspend fun deleteCustomExercise(e: ExerciseEntity) { if (e.isCustom) exercises.delete(e) }

    // ---- Routines ----

    suspend fun loadDraft(routineId: Long): RoutineDraft? {
        val r = routines.routine(routineId) ?: return null
        val byId = exercises.all().associateBy { it.id }
        val days = routines.days(routineId).map { d ->
            DayDraft(d.id, d.name, routines.exercises(d.id).mapNotNull { re ->
                val ex = byId[re.exerciseId] ?: return@mapNotNull null
                ExerciseDraft(re.id, ex.id, ex.name, ex.type, re.targetSets, re.repMin, re.repMax, re.restSeconds, re.notes)
            })
        }
        return RoutineDraft(r.id, r.name, r.notes, days)
    }

    /**
     * Inserts or updates a routine from the draft. Existing days keep their ids (so "next day" still
     * follows workout history); removed days are deleted and new ones inserted. Returns the routine id.
     */
    suspend fun saveDraft(d: RoutineDraft): Long = db.withTransaction {
        val name = d.name.trim().ifEmpty { "My routine" }
        val existing = if (d.id > 0) routines.routine(d.id) else null
        val id = if (existing != null) {
            routines.updateRoutine(existing.copy(name = name, notes = d.notes)); existing.id
        } else routines.insertRoutine(RoutineEntity(name = name, notes = d.notes, position = routines.maxPosition() + 1))
        val oldDays = if (existing != null) routines.days(id).associateBy { it.id } else emptyMap()
        val keep = d.days.map { it.key }.filter { it in oldDays }.toSet()
        oldDays.keys.filter { it !in keep }.forEach { routines.deleteDay(it) }
        d.days.forEachIndexed { di, day ->
            val dayName = day.name.trim().ifEmpty { "Day ${di + 1}" }
            val dayId = if (day.key in keep) {
                routines.updateDay(oldDays.getValue(day.key).copy(name = dayName, position = di))
                routines.deleteExercisesForDay(day.key); day.key
            } else routines.insertDay(RoutineDayEntity(routineId = id, name = dayName, position = di))
            routines.insertExercises(day.exercises.mapIndexed { ei, e ->
                val lo = e.repMin.coerceAtLeast(1)
                RoutineExerciseEntity(
                    dayId = dayId, exerciseId = e.exerciseId, position = ei, targetSets = e.sets.coerceIn(1, 20),
                    repMin = lo, repMax = maxOf(e.repMax, lo), restSeconds = e.restSeconds.coerceIn(0, 900), notes = e.notes,
                )
            })
        }
        if (settings.state.value.activeRoutineId == null) settings.update { it.copy(activeRoutineId = id) }
        id
    }

    suspend fun duplicate(routineId: Long): Long? {
        val d = loadDraft(routineId) ?: return null
        return saveDraft(d.copy(id = 0, name = d.name + " (copy)", days = d.days.map { it.copy(key = TrainingPlanner.newKey()) }))
    }

    suspend fun delete(routineId: Long) {
        routines.deleteRoutine(routineId)
        if (settings.state.value.activeRoutineId == routineId) {
            val next = routines.routines().firstOrNull()?.id
            settings.update { it.copy(activeRoutineId = next) }
        }
    }

    suspend fun move(routineId: Long, delta: Int) = db.withTransaction {
        val list = routines.routines()
        val moved = TrainingPlanner.move(list, list.indexOfFirst { it.id == routineId }, delta)
        routines.updateRoutines(moved.mapIndexed { i, r -> r.copy(position = i) })
    }

    fun setActive(routineId: Long?) = settings.update { it.copy(activeRoutineId = routineId) }

    /** Turns a recommendation into a saved, editable routine. */
    suspend fun saveRecommendation(rec: SplitRecommendation, name: String = rec.name): Long {
        ensureSeeded()
        val bySlug = exercises.bySlugs(rec.days.flatMap { d -> d.exercises.map { it.slug } }.distinct()).associateBy { it.slug }
        val draft = RoutineDraft(name = name, days = rec.days.map { d ->
            DayDraft(TrainingPlanner.newKey(), d.name, d.exercises.mapNotNull { p ->
                val ex = bySlug[p.slug] ?: return@mapNotNull null
                ExerciseDraft(TrainingPlanner.newKey(), ex.id, ex.name, ex.type, p.sets, p.repMin, p.repMax, p.restSeconds, p.notes)
            })
        })
        return saveDraft(draft)
    }

    suspend fun nextDay(routineId: Long): RoutineDayEntity? =
        TrainingPlanner.nextDay(routines.days(routineId), workouts.lastForRoutine(routineId)?.routineDayId)

    // ---- Workouts ----

    /** Starts a workout from a routine day (null = empty workout). If one is already running, returns it. */
    suspend fun startWorkout(routineDayId: Long?): Long = db.withTransaction {
        workouts.active()?.let { return@withTransaction it.id }
        val day = routineDayId?.let { routines.day(it) }
        val now = System.currentTimeMillis()
        val id = workouts.insertWorkout(
            WorkoutEntity(name = day?.name ?: "Workout", epochDay = LocalDate.now().toEpochDay(), startedAt = now,
                routineId = day?.routineId, routineDayId = day?.id),
        )
        if (day != null) {
            val byId = exercises.all().associateBy { it.id }
            for (re in routines.exercises(day.id)) {
                val ex = byId[re.exerciseId] ?: continue
                addExercise(id, ex, re.targetSets, re.repMin, re.repMax, re.restSeconds, re.notes)
            }
        }
        id
    }

    suspend fun addExercise(
        workoutId: Long, ex: ExerciseEntity, targetSets: Int? = null, repMin: Int? = null, repMax: Int? = null,
        restSeconds: Int? = null, notes: String = "",
    ): Long = db.withTransaction {
        val last = workouts.recentOccurrences(ex.id, 1).firstOrNull()
        val unit = last?.weightUnit ?: settings.state.value.trainingUnit
        val position = workouts.exercises(workoutId).size
        val cardio = ex.exerciseType == ExerciseType.CARDIO
        val weId = workouts.insertExercise(
            WorkoutExerciseEntity(
                workoutId = workoutId, exerciseId = ex.id, name = ex.name, type = ex.type, position = position, unit = unit.name,
                // Default ranges for exercises added outside a routine, so double progression has a target.
                repMin = repMin ?: last?.repMin ?: defaultRange(ex.exerciseType)?.first,
                repMax = repMax ?: last?.repMax ?: defaultRange(ex.exerciseType)?.last,
                restSeconds = restSeconds ?: last?.restSeconds ?: if (cardio) 0 else 90, notes = notes,
                isBodyweight = ex.isBodyweight,
            ),
        )
        val lastSets = last?.let { workouts.sets(it.id) }.orEmpty()
        workouts.insertSets(TrainingPlanner.prefill(lastSets, targetSets ?: if (cardio) 1 else null, unit, weId))
        weId
    }

    private fun defaultRange(type: ExerciseType): IntRange? = when (type) {
        ExerciseType.COMPOUND -> 6..10
        ExerciseType.ISOLATION -> 10..15
        ExerciseType.CARDIO -> null
    }

    /** Sets from the most recent finished session of this exercise (excluding this workout). */
    suspend fun lastSets(we: WorkoutExerciseEntity): List<WorkoutSetEntity> {
        val exId = we.exerciseId ?: return emptyList()
        val last = workouts.recentOccurrences(exId, 2).firstOrNull { it.workoutId != we.workoutId } ?: return emptyList()
        return workouts.sets(last.id)
    }

    /** Double-progression suggestion from the last two finished sessions of this exercise. */
    suspend fun suggestion(we: WorkoutExerciseEntity): ProgressionSuggestion? {
        val exId = we.exerciseId ?: return null
        val recent = workouts.recentOccurrences(exId, 2).filter { it.workoutId != we.workoutId }
        val last = recent.firstOrNull() ?: return null
        val repMin = we.repMin ?: last.repMin ?: return null
        val repMax = we.repMax ?: last.repMax ?: return null
        return Progression.suggest(
            workouts.sets(last.id).map { it.toLogged() }, repMin, repMax, we.exerciseType, we.weightUnit,
            recent.getOrNull(1)?.let { p -> workouts.sets(p.id).map { it.toLogged() } },
            bodyweight = we.isBodyweight,
        )
    }

    suspend fun updateWorkout(w: WorkoutEntity) = workouts.updateWorkout(w)
    suspend fun updateExercise(e: WorkoutExerciseEntity) = workouts.updateExercise(e)
    suspend fun setReps(id: Long, reps: Int?) = workouts.setReps(id, reps)
    suspend fun setWeight(id: Long, kg: Double?) = workouts.setWeight(id, kg)
    suspend fun setDone(id: Long, done: Boolean) = workouts.setDone(id, done)
    suspend fun setType(id: Long, type: dev.glinesb.openmacros.domain.training.SetType) = workouts.setType(id, type.name)
    suspend fun deleteSet(id: Long) = workouts.deleteSet(id)

    suspend fun addSet(we: WorkoutExerciseEntity) {
        val sets = workouts.sets(we.id)
        val prev = sets.lastOrNull()
        workouts.insertSet(
            WorkoutSetEntity(workoutExerciseId = we.id, position = (prev?.position ?: -1) + 1, reps = prev?.reps, weightKg = prev?.weightKg, unit = we.unit),
        )
    }

    /** Switches an exercise between lb and kg. Weights stay the same in kg; only the display unit changes. */
    suspend fun setUnit(we: WorkoutExerciseEntity, unit: WeightUnit) = db.withTransaction {
        workouts.updateExercise(we.copy(unit = unit.name))
        workouts.updateSets(workouts.sets(we.id).map { it.copy(unit = unit.name) })
    }

    suspend fun applySuggestion(we: WorkoutExerciseEntity, s: ProgressionSuggestion) {
        val sets = workouts.sets(we.id)
        workouts.updateSets(sets.filter { it.type == dev.glinesb.openmacros.domain.training.SetType.WORKING && !it.done }.map { it.copy(weightKg = s.weightKg ?: it.weightKg, reps = s.targetReps) })
    }

    suspend fun moveExercise(workoutId: Long, weId: Long, delta: Int) = db.withTransaction {
        val list = workouts.exercises(workoutId)
        workouts.updateExercises(TrainingPlanner.move(list, list.indexOfFirst { it.id == weId }, delta).mapIndexed { i, e -> e.copy(position = i) })
    }

    suspend fun removeExercise(weId: Long) = workouts.deleteExercise(weId)

    /** Finishes a workout: keeps only sets marked done, drops exercises left empty. Returns sets kept. */
    suspend fun finish(workoutId: Long, notes: String): Int = db.withTransaction {
        var kept = 0
        for (we in workouts.exercises(workoutId)) {
            val sets = workouts.sets(we.id)
            sets.filter { !it.done || it.reps == null }.forEach { workouts.deleteSet(it.id) }
            val left = sets.count { it.done && it.reps != null }
            if (left == 0) workouts.deleteExercise(we.id)
            kept += left
        }
        workouts.byId(workoutId)?.let { workouts.updateWorkout(it.copy(finishedAt = System.currentTimeMillis(), notes = notes)) }
        kept
    }

    suspend fun deleteWorkout(id: Long) = workouts.deleteWorkout(id)
}
