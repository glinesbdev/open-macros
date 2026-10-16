package dev.glinesb.openmacros.domain.training

import dev.glinesb.openmacros.domain.GoalType
import dev.glinesb.openmacros.domain.training.EquipmentAccess.BODYWEIGHT
import dev.glinesb.openmacros.domain.training.EquipmentAccess.DUMBBELLS
import dev.glinesb.openmacros.domain.training.EquipmentAccess.FULL_GYM
import dev.glinesb.openmacros.domain.training.Experience.ADVANCED
import dev.glinesb.openmacros.domain.training.Experience.BEGINNER
import kotlin.math.roundToInt

data class SplitInput(
    val daysPerWeek: Int = 4,
    val goal: TrainingGoal = TrainingGoal.MUSCLE_GAIN,
    val experience: Experience = Experience.INTERMEDIATE,
    val equipment: EquipmentAccess = FULL_GYM,
)

data class PlannedExercise(val slug: String, val sets: Int, val repMin: Int, val repMax: Int, val restSeconds: Int, val notes: String = "")

data class PlannedDay(val name: String, val exercises: List<PlannedExercise>)

data class SplitRecommendation(
    val id: String,
    val name: String,
    val recommended: Boolean,
    /** Why this split structure (frequency, recovery). */
    val why: String,
    /** How the goal shaped reps, sets and rest. */
    val goalNote: String,
    val days: List<PlannedDay>,
) {
    /** Approximate weekly hard sets per muscle: 1 for a primary muscle, 0.5 for a secondary one. */
    fun weeklySets(): Map<Muscle, Double> {
        val out = LinkedHashMap<Muscle, Double>()
        for (d in days) for (p in d.exercises) {
            val ex = ExerciseLibrary.bySlug[p.slug] ?: continue
            ex.primary.forEach { out[it] = (out[it] ?: 0.0) + p.sets }
            ex.secondary.forEach { out[it] = (out[it] ?: 0.0) + p.sets * 0.5 }
        }
        return out
    }
}

/** Offered (never forced) alongside a goal: what to set the nutrition goal to. */
data class NutritionSuggestion(val goal: GoalType, val rateKgPerWeek: Double, val reason: String)

/**
 * Picks evidence-based split templates for a number of training days, then fills them with exercises
 * for the available equipment and with reps, sets and rest for the goal and experience level.
 *
 * Principles it encodes (see ARCHITECTURE.md for the references):
 *  - train each muscle about twice a week (better than once for hypertrophy at equal volume);
 *  - roughly 10–20 hard sets per muscle per week for growth, fewer for beginners and while cutting;
 *  - heavy, low-rep work on the main lifts when the goal is strength; moderate reps otherwise;
 *  - longer rests on heavy compounds.
 */
object SplitRecommender {
    private enum class Role { MAIN, SECONDARY, ISOLATION }

    private enum class Slot {
        SQUAT, HINGE, HINGE_LIGHT, QUAD_ACC, LUNGE, LEG_EXT, HAM_CURL, GLUTE, CALVES,
        H_PUSH, INCLINE_PUSH, V_PUSH, H_PULL, H_PULL_2, V_PULL, V_PULL_2,
        LATERAL, REAR_DELT, CHEST_ISO, BICEPS, BICEPS_2, TRICEPS, TRICEPS_2, CORE, CORE_2,
    }

    private class SlotUse(val slot: Slot, val role: Role, val optional: Boolean = false)

    private fun m(s: Slot) = SlotUse(s, Role.MAIN)
    private fun s(s: Slot, opt: Boolean = false) = SlotUse(s, Role.SECONDARY, opt)
    private fun i(s: Slot, opt: Boolean = false) = SlotUse(s, Role.ISOLATION, opt)

    private class DayTemplate(val name: String, val slots: List<SlotUse>)

    private val FULL_A = DayTemplate("Full Body A", listOf(m(Slot.SQUAT), m(Slot.H_PUSH), s(Slot.H_PULL), i(Slot.HAM_CURL, true), i(Slot.LATERAL, true), i(Slot.CORE, true)))
    private val FULL_B = DayTemplate("Full Body B", listOf(m(Slot.HINGE), m(Slot.V_PUSH), s(Slot.V_PULL), s(Slot.LUNGE, true), i(Slot.TRICEPS, true), i(Slot.CALVES, true)))
    private val FULL_C = DayTemplate("Full Body C", listOf(s(Slot.QUAD_ACC), m(Slot.INCLINE_PUSH), s(Slot.H_PULL_2), s(Slot.GLUTE), i(Slot.REAR_DELT, true), i(Slot.BICEPS, true)))
    private val UPPER_A = DayTemplate("Upper A", listOf(m(Slot.H_PUSH), m(Slot.H_PULL), s(Slot.V_PUSH), s(Slot.V_PULL), i(Slot.LATERAL, true), i(Slot.BICEPS), i(Slot.TRICEPS, true)))
    private val UPPER_B = DayTemplate("Upper B", listOf(m(Slot.INCLINE_PUSH), m(Slot.V_PULL), s(Slot.H_PULL_2), i(Slot.CHEST_ISO, true), i(Slot.LATERAL), i(Slot.REAR_DELT, true), i(Slot.TRICEPS), i(Slot.BICEPS_2, true)))
    private val LOWER_A = DayTemplate("Lower A", listOf(m(Slot.SQUAT), s(Slot.QUAD_ACC, true), i(Slot.HAM_CURL), s(Slot.GLUTE), i(Slot.CALVES), i(Slot.CORE, true)))
    private val LOWER_B = DayTemplate("Lower B", listOf(m(Slot.HINGE), s(Slot.LUNGE), i(Slot.LEG_EXT, true), i(Slot.HAM_CURL), i(Slot.CALVES), i(Slot.CORE_2, true)))
    private val PUSH = DayTemplate("Push", listOf(m(Slot.H_PUSH), s(Slot.V_PUSH), s(Slot.INCLINE_PUSH, true), i(Slot.LATERAL), i(Slot.TRICEPS), i(Slot.CHEST_ISO, true), i(Slot.TRICEPS_2, true)))
    private val PULL = DayTemplate("Pull", listOf(m(Slot.V_PULL), s(Slot.H_PULL), s(Slot.H_PULL_2, true), i(Slot.REAR_DELT), i(Slot.BICEPS), i(Slot.BICEPS_2, true)))
    private val LEGS = DayTemplate("Legs", listOf(m(Slot.SQUAT), s(Slot.HINGE_LIGHT), s(Slot.QUAD_ACC, true), i(Slot.HAM_CURL), i(Slot.CALVES), i(Slot.CORE, true)))
    private val PUSH_B = DayTemplate("Push B", listOf(m(Slot.V_PUSH), s(Slot.INCLINE_PUSH), i(Slot.CHEST_ISO), i(Slot.LATERAL), i(Slot.TRICEPS_2), i(Slot.TRICEPS, true)))
    private val PULL_B = DayTemplate("Pull B", listOf(m(Slot.H_PULL), s(Slot.V_PULL_2), i(Slot.REAR_DELT), i(Slot.BICEPS_2), i(Slot.BICEPS, true), i(Slot.CORE_2)))
    private val LEGS_B = DayTemplate("Legs B", listOf(m(Slot.HINGE), s(Slot.QUAD_ACC), s(Slot.LUNGE, true), i(Slot.LEG_EXT, true), i(Slot.HAM_CURL), i(Slot.CALVES)))

    private class Template(val id: String, val name: String, val days: List<DayTemplate>, val why: String, val names: List<String>? = null)

    private const val FREQ = "Each muscle gets trained about twice a week, which grows muscle better than once a week at the same total volume."

    private fun templates(days: Int, exp: Experience): List<Template> = when (days) {
        2 -> listOf(
            Template("fb2", "Full Body A/B", listOf(FULL_A, FULL_B), "With two days, full-body sessions are the only way to hit everything twice a week. $FREQ"),
            Template("ul1", "Upper / Lower", listOf(UPPER_A, LOWER_A), "Simpler sessions, but each muscle is trained only once a week; fine for maintenance, slower for growth."),
        )
        3 -> listOf(
            Template("fb3", "Full Body A/B/C", listOf(FULL_A, FULL_B, FULL_C),
                "Three full-body days with different main lifts. $FREQ Great for beginners: lots of practice on the big lifts."),
            Template("ulf", "Upper / Lower / Full", listOf(UPPER_A, LOWER_A, FULL_C), "A mix: focused upper and lower days, then a lighter full-body day so everything gets a second hit."),
            Template("ppl", "Push / Pull / Legs", listOf(PUSH, PULL, LEGS),
                "Popular and easy to follow, but at three days each muscle is trained only once a week; more volume per session to compensate."),
        )
        4 -> listOf(
            Template("ul2", "Upper / Lower ×2", listOf(UPPER_A, LOWER_A, UPPER_B, LOWER_B),
                "The classic 4-day split. $FREQ Sessions stay a manageable length and upper and lower body get a rest day between hits."),
            Template("pplf", "Push / Pull / Legs + Full Body", listOf(PUSH, PULL, LEGS, FULL_B),
                "PPL with a full-body day added so every muscle gets a second, lighter hit each week."),
            Template("fb4", "Full Body ×4", listOf(FULL_A, FULL_B, FULL_A.copy("Full Body C"), FULL_B.copy("Full Body D")),
                "Shorter sessions spread over four days with each lift practised twice a week. Good if you'd rather keep workouts short."),
        )
        5 -> listOf(
            Template("ulppl", "Upper / Lower + Push / Pull / Legs", listOf(UPPER_A, LOWER_A, PUSH, PULL, LEGS),
                "Two upper/lower days plus a PPL block: everything twice a week, with room for extra arm and shoulder work."),
            Template("pplul", "Push / Pull / Legs + Upper / Lower", listOf(PUSH, PULL, LEGS, UPPER_B, LOWER_B),
                "The same weekly structure in a different order. Pick whichever fits your week better."),
        )
        else -> listOf(
            Template("ppl2", "Push / Pull / Legs ×2", listOf(PUSH, PULL, LEGS, PUSH_B, PULL_B, LEGS_B),
                "Six focused sessions; each muscle twice a week with high weekly volume. Best suited to intermediate and advanced lifters who recover well."),
            Template("ul3", "Upper / Lower ×3", listOf(UPPER_A, LOWER_A, UPPER_B, LOWER_B, UPPER_A.copy("Upper C"), LOWER_A.copy("Lower C")),
                "Upper/lower three times a week: shorter, less taxing sessions than PPL with frequent practice on the main lifts."),
        ).let { if (exp == BEGINNER) it.reversed() else it }
    }

    private fun DayTemplate.copy(name: String) = DayTemplate(name, slots)

    fun recommend(input: SplitInput): List<SplitRecommendation> {
        val days = input.daysPerWeek.coerceIn(2, 6)
        val list = templates(days, input.experience)
        val goalNote = goalNote(input)
        val beginnerNote = if (input.experience == BEGINNER && days >= 5)
            " As a beginner, 3–4 sessions a week gets nearly all the benefit; if recovery suffers, drop a day." else ""
        return list.mapIndexed { idx, t ->
            SplitRecommendation(
                id = t.id, name = t.name, recommended = idx == 0,
                why = t.why + beginnerNote,
                goalNote = goalNote,
                days = t.days.map { d -> buildDay(d, input) },
            )
        }
    }

    private fun buildDay(d: DayTemplate, input: SplitInput): PlannedDay {
        val used = HashSet<String>()
        val ex = d.slots.filter { !(it.optional && input.experience == BEGINNER) }.mapNotNull { use ->
            val slug = pick(use.slot, input) ?: return@mapNotNull null
            if (!used.add(slug)) return@mapNotNull null
            prescribe(slug, use.role, input)
        }
        return PlannedDay(d.name, ex)
    }

    /** Exercise for a movement slot given the equipment (and a few goal/experience tweaks). Null = skip. */
    private fun pick(slot: Slot, input: SplitInput): String? {
        val eq = input.equipment; val exp = input.experience
        val strength = input.goal == TrainingGoal.STRENGTH
        return when (slot) {
            Slot.SQUAT -> when (eq) {
                FULL_GYM -> "back-squat"
                DUMBBELLS -> if (exp == BEGINNER) "goblet-squat" else "bulgarian-split-squat"
                BODYWEIGHT -> if (exp == BEGINNER) "bodyweight-squat" else "bodyweight-split-squat"
            }
            Slot.HINGE -> when (eq) {
                FULL_GYM -> if (strength) "deadlift" else "romanian-deadlift"
                DUMBBELLS -> "dumbbell-romanian-deadlift"
                BODYWEIGHT -> "single-leg-glute-bridge"
            }
            Slot.HINGE_LIGHT -> when (eq) { FULL_GYM -> "romanian-deadlift"; DUMBBELLS -> "dumbbell-romanian-deadlift"; BODYWEIGHT -> "glute-bridge" }
            Slot.QUAD_ACC -> when (eq) { FULL_GYM -> "leg-press"; DUMBBELLS -> "step-up"; BODYWEIGHT -> "bodyweight-reverse-lunge" }
            Slot.LUNGE -> when (eq) { FULL_GYM, DUMBBELLS -> "walking-lunge"; BODYWEIGHT -> "bodyweight-reverse-lunge" }
            Slot.LEG_EXT -> if (eq == FULL_GYM) "leg-extension" else null
            Slot.HAM_CURL -> when (eq) {
                FULL_GYM -> "lying-leg-curl"
                DUMBBELLS -> "single-leg-romanian-deadlift"
                BODYWEIGHT -> if (exp == ADVANCED) "nordic-hamstring-curl" else "single-leg-glute-bridge"
            }
            Slot.GLUTE -> if (eq == FULL_GYM) "hip-thrust" else "glute-bridge"
            Slot.CALVES -> when (eq) { FULL_GYM -> "standing-calf-raise"; DUMBBELLS -> "dumbbell-calf-raise"; BODYWEIGHT -> "bodyweight-calf-raise" }
            Slot.H_PUSH -> when (eq) {
                FULL_GYM -> "barbell-bench-press"
                DUMBBELLS -> "dumbbell-bench-press"
                BODYWEIGHT -> if (exp == ADVANCED) "decline-push-up" else "push-up"
            }
            Slot.INCLINE_PUSH -> when (eq) { FULL_GYM, DUMBBELLS -> "incline-dumbbell-press"; BODYWEIGHT -> "decline-push-up" }
            Slot.V_PUSH -> when (eq) { FULL_GYM -> "overhead-press"; DUMBBELLS -> "dumbbell-shoulder-press"; BODYWEIGHT -> "pike-push-up" }
            Slot.H_PULL -> when (eq) {
                FULL_GYM -> if (exp == BEGINNER) "seated-cable-row" else "barbell-row"
                DUMBBELLS -> "dumbbell-row"
                BODYWEIGHT -> "inverted-row"
            }
            Slot.H_PULL_2 -> when (eq) { FULL_GYM, DUMBBELLS -> "chest-supported-dumbbell-row"; BODYWEIGHT -> "inverted-row" }
            Slot.V_PULL -> when (eq) {
                FULL_GYM -> if (exp == BEGINNER) "lat-pulldown" else "pull-up"
                DUMBBELLS -> "dumbbell-pullover"
                BODYWEIGHT -> "pull-up"
            }
            Slot.V_PULL_2 -> when (eq) { FULL_GYM -> "close-grip-lat-pulldown"; DUMBBELLS -> "dumbbell-pullover"; BODYWEIGHT -> "chin-up" }
            Slot.LATERAL -> when (eq) { FULL_GYM -> "cable-lateral-raise"; DUMBBELLS -> "lateral-raise"; BODYWEIGHT -> null }
            Slot.REAR_DELT -> when (eq) { FULL_GYM -> "face-pull"; DUMBBELLS -> "rear-delt-fly"; BODYWEIGHT -> null }
            Slot.CHEST_ISO -> when (eq) { FULL_GYM -> "cable-fly"; DUMBBELLS -> "dumbbell-fly"; BODYWEIGHT -> null }
            Slot.BICEPS -> when (eq) { FULL_GYM -> "ez-bar-curl"; DUMBBELLS -> "dumbbell-curl"; BODYWEIGHT -> "chin-up" }
            Slot.BICEPS_2 -> when (eq) { FULL_GYM, DUMBBELLS -> "hammer-curl"; BODYWEIGHT -> null }
            Slot.TRICEPS -> when (eq) { FULL_GYM -> "triceps-pushdown"; DUMBBELLS -> "dumbbell-overhead-triceps-extension"; BODYWEIGHT -> "diamond-push-up" }
            Slot.TRICEPS_2 -> when (eq) { FULL_GYM -> "overhead-cable-triceps-extension"; DUMBBELLS -> null; BODYWEIGHT -> "bench-dip" }
            Slot.CORE -> when (eq) { FULL_GYM -> "cable-crunch"; DUMBBELLS -> "dead-bug"; BODYWEIGHT -> "plank" }
            Slot.CORE_2 -> when (eq) { FULL_GYM -> "hanging-leg-raise"; DUMBBELLS -> "plank"; BODYWEIGHT -> "side-plank" }
        }
    }

    private data class Rx(val sets: Int, val repMin: Int, val repMax: Int, val rest: Int)

    private fun baseRx(goal: TrainingGoal, role: Role): Rx = when (goal) {
        TrainingGoal.STRENGTH -> when (role) { Role.MAIN -> Rx(4, 3, 5, 180); Role.SECONDARY -> Rx(3, 6, 8, 150); Role.ISOLATION -> Rx(3, 8, 12, 75) }
        TrainingGoal.MUSCLE_GAIN -> when (role) { Role.MAIN -> Rx(3, 6, 10, 150); Role.SECONDARY -> Rx(3, 8, 12, 120); Role.ISOLATION -> Rx(3, 10, 15, 75) }
        TrainingGoal.FAT_LOSS -> when (role) { Role.MAIN -> Rx(3, 5, 8, 150); Role.SECONDARY -> Rx(3, 8, 12, 90); Role.ISOLATION -> Rx(2, 10, 15, 60) }
        TrainingGoal.RECOMP -> when (role) { Role.MAIN -> Rx(3, 6, 10, 120); Role.SECONDARY -> Rx(3, 8, 12, 90); Role.ISOLATION -> Rx(2, 10, 15, 60) }
    }

    private fun prescribe(slug: String, role: Role, input: SplitInput): PlannedExercise {
        var rx = baseRx(input.goal, role)
        when (input.experience) {
            BEGINNER -> rx = rx.copy(sets = rx.sets.coerceAtMost(3))
            ADVANCED -> if (role != Role.ISOLATION && !(input.goal == TrainingGoal.FAT_LOSS && role == Role.SECONDARY)) rx = rx.copy(sets = rx.sets + 1)
            else -> {}
        }
        val seed = ExerciseLibrary.bySlug.getValue(slug)
        val unloaded = seed.equipment == Equipment.BODYWEIGHT
        if (unloaded) {
            rx = if (role == Role.ISOLATION) rx.copy(repMin = 12, repMax = 20)
            else if (input.goal == TrainingGoal.STRENGTH) rx.copy(repMin = 5, repMax = 10) else rx.copy(repMin = 8, repMax = 15)
        }
        if (slug == "plank" || slug == "side-plank") rx = rx.copy(repMin = 30, repMax = 60)
        val notes = buildList {
            if (role == Role.MAIN && input.goal == TrainingGoal.STRENGTH) add("Main lift: heavy, leave 1–2 reps in reserve.")
            if (unloaded && seed.primary.none { it == Muscle.ABS || it == Muscle.OBLIQUES }) add("Past the top of the range? Add load or use a harder variation.")
            if (slug == "pull-up" || slug == "chin-up" || slug == "hanging-leg-raise") add("Needs a pull-up bar.")
            if (slug == "dumbbell-pullover" && input.equipment == DUMBBELLS) add("Stands in for pull-ups; use pull-ups if you have a bar.")
            if (slug == "plank" || slug == "side-plank") add("Log the seconds held as reps.")
        }.joinToString(" ")
        return PlannedExercise(slug, rx.sets, rx.repMin, rx.repMax, rx.rest, notes)
    }

    private fun goalNote(input: SplitInput): String = when (input.goal) {
        TrainingGoal.MUSCLE_GAIN -> "Bulk: main lifts at 6–10 reps and accessories at 8–15, about 10–20 hard sets per muscle a week. " +
            "Muscle grows across a wide rep range as long as sets are taken close to failure."
        TrainingGoal.FAT_LOSS -> "Cut: main lifts stay heavy (5–8 reps) to hold on to strength and muscle in a calorie deficit, while total " +
            "volume is trimmed a little because recovery is lower when eating less."
        TrainingGoal.RECOMP -> "Maintain / recomp: moderate reps (6–15) and moderate volume. With enough protein, newer lifters can build " +
            "some muscle while slowly losing fat."
        TrainingGoal.STRENGTH -> "Strength: main lifts at 3–5 reps with 3-minute rests (heavy loads build max strength best); accessories " +
            "at 6–12 reps to keep building muscle that supports the main lifts."
    } + when (input.experience) {
        BEGINNER -> " Beginner: 3 sets max and fewer exercises per day, so you can focus on technique."
        ADVANCED -> " Advanced: an extra set on the main and secondary lifts."
        else -> ""
    } + if (input.equipment == BODYWEIGHT) " Bodyweight: higher rep ranges, since load is fixed." else ""

    /** What to suggest for the nutrition goal for a training goal, or null when no change makes sense. */
    fun nutritionFor(goal: TrainingGoal, experience: Experience, bodyWeightKg: Double): NutritionSuggestion? = when (goal) {
        TrainingGoal.FAT_LOSS -> NutritionSuggestion(
            GoalType.LOSE, roundRate((bodyWeightKg * 0.0075).coerceIn(0.25, 1.0)),
            "A cut works best at about 0.5–1% of body weight a week, which helps keep muscle while losing fat.",
        )
        TrainingGoal.MUSCLE_GAIN -> {
            val pct = when (experience) { BEGINNER -> 0.005; Experience.INTERMEDIATE -> 0.0035; ADVANCED -> 0.0025 }
            NutritionSuggestion(
                GoalType.GAIN, roundRate((bodyWeightKg * pct).coerceIn(0.1, 0.5)),
                "A lean bulk gains slowly (about ${(pct * 100).toString().trimEnd('0').trimEnd('.')}% of body weight a week at your level) to limit fat gain.",
            )
        }
        TrainingGoal.RECOMP -> NutritionSuggestion(GoalType.MAINTAIN, 0.0, "Recomp means eating around maintenance with plenty of protein.")
        TrainingGoal.STRENGTH -> null
    }

    private fun roundRate(kg: Double): Double = (kg / 0.05).roundToInt() * 0.05
}
