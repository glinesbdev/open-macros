package dev.glinesb.openmacros.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.glinesb.openmacros.domain.FoodItem
import dev.glinesb.openmacros.domain.FoodSource
import dev.glinesb.openmacros.domain.Macros
import kotlinx.serialization.Serializable

/**
 * A food the user has used, favourited, scanned, or created. Remote search hits are cached here the
 * first time they're opened, which is what powers "Recent". Nutrition is per one serving.
 */
@Serializable
@Entity(
    tableName = "foods",
    indices = [Index(value = ["source", "sourceId"], unique = true), Index("barcode"), Index("lastUsedAt")],
)
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val sourceId: String,
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val servingLabel: String,
    val servingGrams: Double? = null,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val isFavorite: Boolean = false,
    val lastUsedAt: Long = 0,
    val useCount: Int = 0,
) {
    val foodSource: FoodSource get() = runCatching { FoodSource.valueOf(source) }.getOrDefault(FoodSource.CUSTOM)
    val perServing: Macros get() = Macros(kcal, protein, carbs, fat)
    val isCustom: Boolean get() = foodSource == FoodSource.CUSTOM

    fun toItem() = FoodItem(foodSource, sourceId, name, brand, barcode, servingLabel, servingGrams, perServing)

    companion object {
        fun from(item: FoodItem) = FoodEntity(
            source = item.source.name, sourceId = item.sourceId, name = item.name, brand = item.brand,
            barcode = item.barcode, servingLabel = item.servingLabel, servingGrams = item.servingGrams,
            kcal = item.perServing.kcal, protein = item.perServing.protein,
            carbs = item.perServing.carbs, fat = item.perServing.fat,
        )
    }
}

/**
 * One line in the food diary. Totals are snapshotted at log time so editing or deleting a food
 * later never rewrites history.
 */
@Serializable
@Entity(tableName = "log_entries", indices = [Index("epochDay"), Index("foodId")])
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    /** [dev.glinesb.openmacros.domain.Meal] name. */
    val meal: String,
    val foodId: Long? = null,
    val name: String,
    val brand: String? = null,
    val amount: Double,
    /** [dev.glinesb.openmacros.domain.AmountUnit] name. */
    val unit: String,
    val servings: Double,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val isQuickAdd: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val macros: Macros get() = Macros(kcal, protein, carbs, fat)
}

/** One weigh-in per day (a new entry for the same day replaces the old one). Stored in kg. */
@Serializable
@Entity(tableName = "weights", indices = [Index(value = ["epochDay"], unique = true)])
data class WeightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val weightKg: Double,
    val createdAt: Long = System.currentTimeMillis(),
)
