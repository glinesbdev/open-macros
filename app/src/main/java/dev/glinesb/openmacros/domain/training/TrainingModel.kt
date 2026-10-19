package dev.glinesb.openmacros.domain.training

enum class Muscle(val label: String) {
    CHEST("Chest"), LATS("Lats"), UPPER_BACK("Upper back"), LOWER_BACK("Lower back"), TRAPS("Traps"),
    FRONT_DELTS("Front delts"), SIDE_DELTS("Side delts"), REAR_DELTS("Rear delts"),
    BICEPS("Biceps"), TRICEPS("Triceps"), FOREARMS("Forearms"),
    QUADS("Quads"), HAMSTRINGS("Hamstrings"), GLUTES("Glutes"), CALVES("Calves"),
    ADDUCTORS("Adductors"), ABDUCTORS("Abductors"), ABS("Abs"), OBLIQUES("Obliques"),
    CARDIOVASCULAR("Cardio"),
    ;

    companion object {
        fun parseList(csv: String): List<Muscle> =
            csv.split(',').mapNotNull { s -> entries.firstOrNull { it.name == s.trim() } }
        fun toCsv(list: List<Muscle>): String = list.joinToString(",") { it.name }
    }
}

enum class Equipment(val label: String) {
    BARBELL("Barbell"), DUMBBELL("Dumbbell"), CABLE("Cable"), MACHINE("Machine"), BODYWEIGHT("Bodyweight"),
    KETTLEBELL("Kettlebell"), BAND("Band"), EZ_BAR("EZ bar"), TRAP_BAR("Trap bar"), OTHER("Other"),
}

/**
 * True for exercises where the load is your body (push-ups, pull-ups, dips…): sets log "BW" plus optional
 * added weight (+) or assistance (−). Cardio is excluded; the assisted pull-up machine counts (assistance).
 */
fun isBodyweightLoad(equipment: Equipment, type: ExerciseType, slug: String): Boolean =
    (equipment == Equipment.BODYWEIGHT && type != ExerciseType.CARDIO) || slug == "assisted-pull-up"

enum class ExerciseType(val label: String) { COMPOUND("Compound"), ISOLATION("Isolation"), CARDIO("Cardio") }

enum class SetType(val label: String, val short: String) { WARMUP("Warm-up", "W"), WORKING("Working", "") }

enum class TrainingGoal(val label: String) {
    MUSCLE_GAIN("Muscle gain (bulk)"), FAT_LOSS("Fat loss (cut)"), RECOMP("Maintain / recomp"), STRENGTH("Strength"),
}

enum class Experience(val label: String, val description: String) {
    BEGINNER("Beginner", "Under ~1 year of consistent lifting"),
    INTERMEDIATE("Intermediate", "1–3 years; still progressing month to month"),
    ADVANCED("Advanced", "3+ years; progress is slow and needs planning"),
}

enum class EquipmentAccess(val label: String, val allowed: Set<Equipment>) {
    FULL_GYM("Full gym", Equipment.entries.toSet()),
    DUMBBELLS("Dumbbells only", setOf(Equipment.DUMBBELL, Equipment.BODYWEIGHT)),
    BODYWEIGHT("Bodyweight", setOf(Equipment.BODYWEIGHT)),
}
