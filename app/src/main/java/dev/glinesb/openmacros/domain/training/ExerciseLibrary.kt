package dev.glinesb.openmacros.domain.training

import dev.glinesb.openmacros.domain.training.Equipment.*
import dev.glinesb.openmacros.domain.training.ExerciseType.*
import dev.glinesb.openmacros.domain.training.Muscle.*

/** One built-in exercise. [slug] is stable across versions; the database keys seed rows on it. */
data class SeedExercise(
    val slug: String,
    val name: String,
    val primary: List<Muscle>,
    val secondary: List<Muscle>,
    val equipment: Equipment,
    val type: ExerciseType,
)

/**
 * The bundled exercise library. Written for Open Macros (not copied from a dataset): common gym,
 * dumbbell, bodyweight and cardio movements with their main and supporting muscles.
 */
object ExerciseLibrary {
    private fun e(slug: String, name: String, primary: List<Muscle>, secondary: List<Muscle>, eq: Equipment, type: ExerciseType) =
        SeedExercise(slug, name, primary, secondary, eq, type)

    val SEED: List<SeedExercise> = listOf(
        // Chest
        e("barbell-bench-press", "Barbell Bench Press", listOf(CHEST), listOf(TRICEPS, FRONT_DELTS), BARBELL, COMPOUND),
        e("incline-barbell-bench-press", "Incline Barbell Bench Press", listOf(CHEST), listOf(FRONT_DELTS, TRICEPS), BARBELL, COMPOUND),
        e("decline-bench-press", "Decline Bench Press", listOf(CHEST), listOf(TRICEPS), BARBELL, COMPOUND),
        e("dumbbell-bench-press", "Dumbbell Bench Press", listOf(CHEST), listOf(TRICEPS, FRONT_DELTS), DUMBBELL, COMPOUND),
        e("incline-dumbbell-press", "Incline Dumbbell Press", listOf(CHEST), listOf(FRONT_DELTS, TRICEPS), DUMBBELL, COMPOUND),
        e("machine-chest-press", "Machine Chest Press", listOf(CHEST), listOf(TRICEPS, FRONT_DELTS), MACHINE, COMPOUND),
        e("smith-machine-bench-press", "Smith Machine Bench Press", listOf(CHEST), listOf(TRICEPS, FRONT_DELTS), MACHINE, COMPOUND),
        e("cable-fly", "Cable Fly", listOf(CHEST), listOf(FRONT_DELTS), CABLE, ISOLATION),
        e("dumbbell-fly", "Dumbbell Fly", listOf(CHEST), listOf(FRONT_DELTS), DUMBBELL, ISOLATION),
        e("pec-deck", "Pec Deck", listOf(CHEST), listOf(FRONT_DELTS), MACHINE, ISOLATION),
        e("push-up", "Push-Up", listOf(CHEST), listOf(TRICEPS, FRONT_DELTS, ABS), BODYWEIGHT, COMPOUND),
        e("decline-push-up", "Decline Push-Up", listOf(CHEST), listOf(FRONT_DELTS, TRICEPS), BODYWEIGHT, COMPOUND),
        e("dip", "Dip", listOf(CHEST, TRICEPS), listOf(FRONT_DELTS), BODYWEIGHT, COMPOUND),
        // Back
        e("deadlift", "Deadlift", listOf(HAMSTRINGS, GLUTES, LOWER_BACK), listOf(UPPER_BACK, TRAPS, QUADS, FOREARMS), BARBELL, COMPOUND),
        e("trap-bar-deadlift", "Trap Bar Deadlift", listOf(QUADS, GLUTES, HAMSTRINGS), listOf(LOWER_BACK, TRAPS, FOREARMS), TRAP_BAR, COMPOUND),
        e("pull-up", "Pull-Up", listOf(LATS), listOf(BICEPS, UPPER_BACK), BODYWEIGHT, COMPOUND),
        e("chin-up", "Chin-Up", listOf(LATS, BICEPS), listOf(UPPER_BACK), BODYWEIGHT, COMPOUND),
        e("assisted-pull-up", "Assisted Pull-Up", listOf(LATS), listOf(BICEPS, UPPER_BACK), MACHINE, COMPOUND),
        e("lat-pulldown", "Lat Pulldown", listOf(LATS), listOf(BICEPS, UPPER_BACK), CABLE, COMPOUND),
        e("close-grip-lat-pulldown", "Close-Grip Lat Pulldown", listOf(LATS), listOf(BICEPS, UPPER_BACK), CABLE, COMPOUND),
        e("barbell-row", "Barbell Row", listOf(UPPER_BACK, LATS), listOf(BICEPS, REAR_DELTS, LOWER_BACK), BARBELL, COMPOUND),
        e("dumbbell-row", "One-Arm Dumbbell Row", listOf(LATS, UPPER_BACK), listOf(BICEPS, REAR_DELTS), DUMBBELL, COMPOUND),
        e("chest-supported-dumbbell-row", "Chest-Supported Dumbbell Row", listOf(UPPER_BACK, LATS), listOf(BICEPS, REAR_DELTS), DUMBBELL, COMPOUND),
        e("seated-cable-row", "Seated Cable Row", listOf(UPPER_BACK, LATS), listOf(BICEPS, REAR_DELTS), CABLE, COMPOUND),
        e("t-bar-row", "T-Bar Row", listOf(UPPER_BACK, LATS), listOf(BICEPS, LOWER_BACK), BARBELL, COMPOUND),
        e("machine-row", "Machine Row", listOf(UPPER_BACK, LATS), listOf(BICEPS, REAR_DELTS), MACHINE, COMPOUND),
        e("inverted-row", "Inverted Row", listOf(UPPER_BACK, LATS), listOf(BICEPS, REAR_DELTS), BODYWEIGHT, COMPOUND),
        e("straight-arm-pulldown", "Straight-Arm Pulldown", listOf(LATS), listOf(TRICEPS), CABLE, ISOLATION),
        e("dumbbell-pullover", "Dumbbell Pullover", listOf(LATS), listOf(CHEST, TRICEPS), DUMBBELL, ISOLATION),
        e("face-pull", "Face Pull", listOf(REAR_DELTS), listOf(UPPER_BACK, TRAPS), CABLE, ISOLATION),
        e("barbell-shrug", "Barbell Shrug", listOf(TRAPS), listOf(FOREARMS), BARBELL, ISOLATION),
        e("dumbbell-shrug", "Dumbbell Shrug", listOf(TRAPS), listOf(FOREARMS), DUMBBELL, ISOLATION),
        e("back-extension", "Back Extension", listOf(LOWER_BACK), listOf(GLUTES, HAMSTRINGS), BODYWEIGHT, ISOLATION),
        // Shoulders
        e("overhead-press", "Overhead Press", listOf(FRONT_DELTS), listOf(TRICEPS, SIDE_DELTS, ABS), BARBELL, COMPOUND),
        e("dumbbell-shoulder-press", "Dumbbell Shoulder Press", listOf(FRONT_DELTS), listOf(TRICEPS, SIDE_DELTS), DUMBBELL, COMPOUND),
        e("arnold-press", "Arnold Press", listOf(FRONT_DELTS), listOf(SIDE_DELTS, TRICEPS), DUMBBELL, COMPOUND),
        e("machine-shoulder-press", "Machine Shoulder Press", listOf(FRONT_DELTS), listOf(TRICEPS, SIDE_DELTS), MACHINE, COMPOUND),
        e("pike-push-up", "Pike Push-Up", listOf(FRONT_DELTS), listOf(TRICEPS, SIDE_DELTS), BODYWEIGHT, COMPOUND),
        e("lateral-raise", "Dumbbell Lateral Raise", listOf(SIDE_DELTS), listOf(TRAPS), DUMBBELL, ISOLATION),
        e("cable-lateral-raise", "Cable Lateral Raise", listOf(SIDE_DELTS), emptyList(), CABLE, ISOLATION),
        e("machine-lateral-raise", "Machine Lateral Raise", listOf(SIDE_DELTS), emptyList(), MACHINE, ISOLATION),
        e("front-raise", "Front Raise", listOf(FRONT_DELTS), emptyList(), DUMBBELL, ISOLATION),
        e("rear-delt-fly", "Dumbbell Rear Delt Fly", listOf(REAR_DELTS), listOf(UPPER_BACK), DUMBBELL, ISOLATION),
        e("reverse-pec-deck", "Reverse Pec Deck", listOf(REAR_DELTS), listOf(UPPER_BACK), MACHINE, ISOLATION),
        e("band-pull-apart", "Band Pull-Apart", listOf(REAR_DELTS), listOf(UPPER_BACK), BAND, ISOLATION),
        e("upright-row", "Upright Row", listOf(SIDE_DELTS, TRAPS), listOf(BICEPS), EZ_BAR, COMPOUND),
        // Arms
        e("barbell-curl", "Barbell Curl", listOf(BICEPS), listOf(FOREARMS), BARBELL, ISOLATION),
        e("ez-bar-curl", "EZ-Bar Curl", listOf(BICEPS), listOf(FOREARMS), EZ_BAR, ISOLATION),
        e("dumbbell-curl", "Dumbbell Curl", listOf(BICEPS), listOf(FOREARMS), DUMBBELL, ISOLATION),
        e("hammer-curl", "Hammer Curl", listOf(BICEPS, FOREARMS), emptyList(), DUMBBELL, ISOLATION),
        e("incline-dumbbell-curl", "Incline Dumbbell Curl", listOf(BICEPS), emptyList(), DUMBBELL, ISOLATION),
        e("preacher-curl", "Preacher Curl", listOf(BICEPS), emptyList(), EZ_BAR, ISOLATION),
        e("cable-curl", "Cable Curl", listOf(BICEPS), listOf(FOREARMS), CABLE, ISOLATION),
        e("concentration-curl", "Concentration Curl", listOf(BICEPS), emptyList(), DUMBBELL, ISOLATION),
        e("triceps-pushdown", "Triceps Pushdown", listOf(TRICEPS), emptyList(), CABLE, ISOLATION),
        e("overhead-cable-triceps-extension", "Overhead Cable Triceps Extension", listOf(TRICEPS), emptyList(), CABLE, ISOLATION),
        e("dumbbell-overhead-triceps-extension", "Dumbbell Overhead Triceps Extension", listOf(TRICEPS), emptyList(), DUMBBELL, ISOLATION),
        e("skull-crusher", "Skull Crusher", listOf(TRICEPS), emptyList(), EZ_BAR, ISOLATION),
        e("close-grip-bench-press", "Close-Grip Bench Press", listOf(TRICEPS), listOf(CHEST, FRONT_DELTS), BARBELL, COMPOUND),
        e("bench-dip", "Bench Dip", listOf(TRICEPS), listOf(CHEST, FRONT_DELTS), BODYWEIGHT, COMPOUND),
        e("diamond-push-up", "Diamond Push-Up", listOf(TRICEPS), listOf(CHEST, FRONT_DELTS), BODYWEIGHT, COMPOUND),
        e("wrist-curl", "Wrist Curl", listOf(FOREARMS), emptyList(), DUMBBELL, ISOLATION),
        // Legs
        e("back-squat", "Barbell Back Squat", listOf(QUADS, GLUTES), listOf(ADDUCTORS, LOWER_BACK), BARBELL, COMPOUND),
        e("front-squat", "Front Squat", listOf(QUADS), listOf(GLUTES, ABS, UPPER_BACK), BARBELL, COMPOUND),
        e("goblet-squat", "Goblet Squat", listOf(QUADS, GLUTES), listOf(ABS), DUMBBELL, COMPOUND),
        e("leg-press", "Leg Press", listOf(QUADS, GLUTES), listOf(ADDUCTORS), MACHINE, COMPOUND),
        e("hack-squat", "Hack Squat", listOf(QUADS), listOf(GLUTES), MACHINE, COMPOUND),
        e("smith-machine-squat", "Smith Machine Squat", listOf(QUADS, GLUTES), emptyList(), MACHINE, COMPOUND),
        e("bulgarian-split-squat", "Bulgarian Split Squat", listOf(QUADS, GLUTES), listOf(ADDUCTORS, HAMSTRINGS), DUMBBELL, COMPOUND),
        e("walking-lunge", "Dumbbell Walking Lunge", listOf(QUADS, GLUTES), listOf(HAMSTRINGS, ADDUCTORS), DUMBBELL, COMPOUND),
        e("step-up", "Dumbbell Step-Up", listOf(QUADS, GLUTES), listOf(HAMSTRINGS), DUMBBELL, COMPOUND),
        e("bodyweight-squat", "Bodyweight Squat", listOf(QUADS, GLUTES), emptyList(), BODYWEIGHT, COMPOUND),
        e("bodyweight-reverse-lunge", "Reverse Lunge (Bodyweight)", listOf(QUADS, GLUTES), listOf(HAMSTRINGS), BODYWEIGHT, COMPOUND),
        e("bodyweight-split-squat", "Split Squat (Bodyweight)", listOf(QUADS, GLUTES), listOf(ADDUCTORS), BODYWEIGHT, COMPOUND),
        e("romanian-deadlift", "Romanian Deadlift", listOf(HAMSTRINGS, GLUTES), listOf(LOWER_BACK, FOREARMS), BARBELL, COMPOUND),
        e("dumbbell-romanian-deadlift", "Dumbbell Romanian Deadlift", listOf(HAMSTRINGS, GLUTES), listOf(LOWER_BACK), DUMBBELL, COMPOUND),
        e("single-leg-romanian-deadlift", "Single-Leg Romanian Deadlift", listOf(HAMSTRINGS, GLUTES), listOf(LOWER_BACK), DUMBBELL, COMPOUND),
        e("good-morning", "Good Morning", listOf(HAMSTRINGS, LOWER_BACK), listOf(GLUTES), BARBELL, COMPOUND),
        e("lying-leg-curl", "Lying Leg Curl", listOf(HAMSTRINGS), listOf(CALVES), MACHINE, ISOLATION),
        e("seated-leg-curl", "Seated Leg Curl", listOf(HAMSTRINGS), emptyList(), MACHINE, ISOLATION),
        e("nordic-hamstring-curl", "Nordic Hamstring Curl", listOf(HAMSTRINGS), emptyList(), BODYWEIGHT, ISOLATION),
        e("leg-extension", "Leg Extension", listOf(QUADS), emptyList(), MACHINE, ISOLATION),
        e("hip-thrust", "Barbell Hip Thrust", listOf(GLUTES), listOf(HAMSTRINGS), BARBELL, COMPOUND),
        e("glute-bridge", "Glute Bridge", listOf(GLUTES), listOf(HAMSTRINGS), BODYWEIGHT, ISOLATION),
        e("single-leg-glute-bridge", "Single-Leg Glute Bridge", listOf(GLUTES), listOf(HAMSTRINGS), BODYWEIGHT, ISOLATION),
        e("hip-adduction", "Hip Adduction Machine", listOf(ADDUCTORS), emptyList(), MACHINE, ISOLATION),
        e("hip-abduction", "Hip Abduction Machine", listOf(ABDUCTORS), listOf(GLUTES), MACHINE, ISOLATION),
        e("standing-calf-raise", "Standing Calf Raise", listOf(CALVES), emptyList(), MACHINE, ISOLATION),
        e("seated-calf-raise", "Seated Calf Raise", listOf(CALVES), emptyList(), MACHINE, ISOLATION),
        e("dumbbell-calf-raise", "Single-Leg Dumbbell Calf Raise", listOf(CALVES), emptyList(), DUMBBELL, ISOLATION),
        e("bodyweight-calf-raise", "Bodyweight Calf Raise", listOf(CALVES), emptyList(), BODYWEIGHT, ISOLATION),
        e("kettlebell-swing", "Kettlebell Swing", listOf(GLUTES, HAMSTRINGS), listOf(LOWER_BACK, ABS), KETTLEBELL, COMPOUND),
        // Core and carries
        e("plank", "Plank", listOf(ABS), listOf(OBLIQUES), BODYWEIGHT, ISOLATION),
        e("side-plank", "Side Plank", listOf(OBLIQUES), listOf(ABS), BODYWEIGHT, ISOLATION),
        e("hanging-leg-raise", "Hanging Leg Raise", listOf(ABS), listOf(OBLIQUES, FOREARMS), BODYWEIGHT, ISOLATION),
        e("cable-crunch", "Cable Crunch", listOf(ABS), emptyList(), CABLE, ISOLATION),
        e("crunch", "Crunch", listOf(ABS), emptyList(), BODYWEIGHT, ISOLATION),
        e("ab-wheel-rollout", "Ab Wheel Rollout", listOf(ABS), listOf(LATS, OBLIQUES), OTHER, ISOLATION),
        e("dead-bug", "Dead Bug", listOf(ABS), emptyList(), BODYWEIGHT, ISOLATION),
        e("russian-twist", "Russian Twist", listOf(OBLIQUES), listOf(ABS), BODYWEIGHT, ISOLATION),
        e("pallof-press", "Pallof Press", listOf(OBLIQUES), listOf(ABS), CABLE, ISOLATION),
        e("farmers-carry", "Farmer's Carry", listOf(FOREARMS, TRAPS), listOf(ABS, OBLIQUES), DUMBBELL, COMPOUND),
        // Cardio (logged as minutes)
        e("running", "Running", listOf(CARDIOVASCULAR), listOf(QUADS, CALVES), BODYWEIGHT, CARDIO),
        e("treadmill", "Treadmill", listOf(CARDIOVASCULAR), listOf(QUADS, CALVES), MACHINE, CARDIO),
        e("incline-walk", "Walking / Incline Walk", listOf(CARDIOVASCULAR), listOf(GLUTES, CALVES), BODYWEIGHT, CARDIO),
        e("stationary-bike", "Stationary Bike", listOf(CARDIOVASCULAR), listOf(QUADS), MACHINE, CARDIO),
        e("cycling", "Cycling", listOf(CARDIOVASCULAR), listOf(QUADS), OTHER, CARDIO),
        e("rowing-machine", "Rowing Machine", listOf(CARDIOVASCULAR), listOf(UPPER_BACK, QUADS), MACHINE, CARDIO),
        e("elliptical", "Elliptical", listOf(CARDIOVASCULAR), emptyList(), MACHINE, CARDIO),
        e("stair-climber", "Stair Climber", listOf(CARDIOVASCULAR), listOf(GLUTES, QUADS), MACHINE, CARDIO),
        e("jump-rope", "Jump Rope", listOf(CARDIOVASCULAR), listOf(CALVES), OTHER, CARDIO),
        e("swimming", "Swimming", listOf(CARDIOVASCULAR), listOf(LATS), OTHER, CARDIO),
        e("burpee", "Burpees", listOf(CARDIOVASCULAR), listOf(CHEST, QUADS), BODYWEIGHT, CARDIO),
    )

    val bySlug: Map<String, SeedExercise> = SEED.associateBy { it.slug }
}
