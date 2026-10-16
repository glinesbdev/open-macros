package dev.glinesb.openmacros.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun byId(id: Long): FoodEntity?

    @Query("SELECT * FROM foods WHERE id = :id")
    fun observe(id: Long): Flow<FoodEntity?>

    @Query("SELECT * FROM foods WHERE source = :source AND sourceId = :sourceId")
    suspend fun bySource(source: String, sourceId: String): FoodEntity?

    @Query("SELECT * FROM foods WHERE barcode = :barcode ORDER BY lastUsedAt DESC LIMIT 1")
    suspend fun byBarcode(barcode: String): FoodEntity?

    @Query("SELECT * FROM foods WHERE lastUsedAt > 0 ORDER BY lastUsedAt DESC LIMIT :limit")
    fun recents(limit: Int = 40): Flow<List<FoodEntity>>

    @Query("SELECT * FROM foods WHERE isFavorite = 1 ORDER BY name COLLATE NOCASE")
    fun favorites(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM foods WHERE source = 'CUSTOM' ORDER BY name COLLATE NOCASE")
    fun customFoods(): Flow<List<FoodEntity>>

    /** Local matches (custom foods, favourites, anything used before) for a search query. */
    @Query(
        "SELECT * FROM foods WHERE (source = 'CUSTOM' OR isFavorite = 1 OR lastUsedAt > 0) " +
            "AND (name LIKE '%' || :q || '%' OR brand LIKE '%' || :q || '%') ORDER BY useCount DESC LIMIT 10",
    )
    suspend fun searchLocal(q: String): List<FoodEntity>

    @Query("SELECT * FROM foods")
    suspend fun all(): List<FoodEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(food: FoodEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(foods: List<FoodEntity>)

    @Update
    suspend fun update(food: FoodEntity)

    @Query("UPDATE foods SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE foods SET lastUsedAt = :now, useCount = useCount + 1 WHERE id = :id")
    suspend fun markUsed(id: Long, now: Long)

    @Delete
    suspend fun delete(food: FoodEntity)

    @Query("DELETE FROM foods")
    suspend fun deleteAll()
}

@Dao
interface LogDao {
    @Query("SELECT * FROM log_entries WHERE epochDay = :day ORDER BY createdAt")
    fun forDay(day: Long): Flow<List<LogEntryEntity>>

    @Query("SELECT * FROM log_entries WHERE id = :id")
    suspend fun byId(id: Long): LogEntryEntity?

    @Query("SELECT * FROM log_entries ORDER BY epochDay, createdAt")
    suspend fun all(): List<LogEntryEntity>

    @Insert
    suspend fun insert(entry: LogEntryEntity): Long

    @Insert
    suspend fun insertAll(entries: List<LogEntryEntity>)

    @Update
    suspend fun update(entry: LogEntryEntity)

    @Delete
    suspend fun delete(entry: LogEntryEntity)

    @Query("DELETE FROM log_entries")
    suspend fun deleteAll()

    /** Keeps history when a food is deleted: entries keep their snapshot but lose the link. */
    @Query("UPDATE log_entries SET foodId = NULL WHERE foodId = :foodId")
    suspend fun unlinkFood(foodId: Long)
}

@Dao
interface WeightDao {
    @Query("SELECT * FROM weights ORDER BY epochDay")
    fun observeAll(): Flow<List<WeightEntity>>

    @Query("SELECT * FROM weights ORDER BY epochDay")
    suspend fun all(): List<WeightEntity>

    @Query("SELECT * FROM weights WHERE epochDay = :day")
    suspend fun forDay(day: Long): WeightEntity?

    /** Replaces any existing entry for the same day (unique index on epochDay). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WeightEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<WeightEntity>)

    @Delete
    suspend fun delete(entry: WeightEntity)

    @Query("DELETE FROM weights")
    suspend fun deleteAll()
}
