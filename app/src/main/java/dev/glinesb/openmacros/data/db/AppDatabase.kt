package dev.glinesb.openmacros.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FoodEntity::class, LogEntryEntity::class, WeightEntity::class,
        ExerciseEntity::class, RoutineEntity::class, RoutineDayEntity::class, RoutineExerciseEntity::class,
        WorkoutEntity::class, WorkoutExerciseEntity::class, WorkoutSetEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foods(): FoodDao
    abstract fun log(): LogDao
    abstract fun weights(): WeightDao
    abstract fun exercises(): ExerciseDao
    abstract fun routines(): RoutineDao
    abstract fun workouts(): WorkoutDao

    companion object {
        /** v1 → v2 adds the training tables. Existing food, diary and weight tables are untouched. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Migrations.SQL_1_2.forEach(db::execSQL)
            }
        }

        /** v2 → v3: bodyweight flag on workout exercises; old 0-weight sets on bodyweight exercises become plain BW. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Migrations.SQL_2_3.forEach(db::execSQL)
            }
        }

        // No fallbackToDestructiveMigration: a missing migration must crash in testing rather than wipe a diary.
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "open-macros.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
