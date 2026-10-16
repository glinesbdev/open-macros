package dev.glinesb.openmacros.data.db

/**
 * Plain SQL for each migration, kept free of Android types so a JVM test can run it on real SQLite
 * and check the result against Room's exported schema (app/schemas).
 */
object Migrations {
    /** v1 → v2: adds the training tables. Creates only; never drops or alters existing tables. */
    val SQL_1_2: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `exercises` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `slug` TEXT NOT NULL, `name` TEXT NOT NULL, `primaryMuscles` TEXT NOT NULL, `secondaryMuscles` TEXT NOT NULL, `equipment` TEXT NOT NULL, `type` TEXT NOT NULL, `isCustom` INTEGER NOT NULL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_exercises_slug` ON `exercises` (`slug`)",
        "CREATE INDEX IF NOT EXISTS `index_exercises_name` ON `exercises` (`name`)",
        "CREATE TABLE IF NOT EXISTS `routines` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `notes` TEXT NOT NULL, `position` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS `routine_days` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `routineId` INTEGER NOT NULL, `name` TEXT NOT NULL, `position` INTEGER NOT NULL, FOREIGN KEY(`routineId`) REFERENCES `routines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_routine_days_routineId` ON `routine_days` (`routineId`)",
        "CREATE TABLE IF NOT EXISTS `routine_exercises` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dayId` INTEGER NOT NULL, `exerciseId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `targetSets` INTEGER NOT NULL, `repMin` INTEGER NOT NULL, `repMax` INTEGER NOT NULL, `restSeconds` INTEGER NOT NULL, `notes` TEXT NOT NULL, FOREIGN KEY(`dayId`) REFERENCES `routine_days`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_routine_exercises_dayId` ON `routine_exercises` (`dayId`)",
        "CREATE INDEX IF NOT EXISTS `index_routine_exercises_exerciseId` ON `routine_exercises` (`exerciseId`)",
        "CREATE TABLE IF NOT EXISTS `workouts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `epochDay` INTEGER NOT NULL, `startedAt` INTEGER NOT NULL, `finishedAt` INTEGER, `routineId` INTEGER, `routineDayId` INTEGER, `notes` TEXT NOT NULL)",
        "CREATE INDEX IF NOT EXISTS `index_workouts_epochDay` ON `workouts` (`epochDay`)",
        "CREATE INDEX IF NOT EXISTS `index_workouts_finishedAt` ON `workouts` (`finishedAt`)",
        "CREATE TABLE IF NOT EXISTS `workout_exercises` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `workoutId` INTEGER NOT NULL, `exerciseId` INTEGER, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `position` INTEGER NOT NULL, `unit` TEXT NOT NULL, `repMin` INTEGER, `repMax` INTEGER, `restSeconds` INTEGER NOT NULL, `notes` TEXT NOT NULL, FOREIGN KEY(`workoutId`) REFERENCES `workouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        "CREATE INDEX IF NOT EXISTS `index_workout_exercises_workoutId` ON `workout_exercises` (`workoutId`)",
        "CREATE INDEX IF NOT EXISTS `index_workout_exercises_exerciseId` ON `workout_exercises` (`exerciseId`)",
        "CREATE TABLE IF NOT EXISTS `workout_sets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `workoutExerciseId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `setType` TEXT NOT NULL, `reps` INTEGER, `weightKg` REAL, `unit` TEXT NOT NULL, `done` INTEGER NOT NULL, FOREIGN KEY(`workoutExerciseId`) REFERENCES `workout_exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_workout_sets_workoutExerciseId` ON `workout_sets` (`workoutExerciseId`)",
    )
}
