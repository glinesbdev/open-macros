package dev.glinesb.openmacros.data

import androidx.room.withTransaction
import dev.glinesb.openmacros.data.db.AppDatabase
import dev.glinesb.openmacros.data.db.FoodEntity
import dev.glinesb.openmacros.data.db.LogEntryEntity
import dev.glinesb.openmacros.data.db.WeightEntity
import dev.glinesb.openmacros.data.remote.HttpStatusException
import dev.glinesb.openmacros.data.remote.OpenFoodFactsApi
import dev.glinesb.openmacros.data.remote.UsdaApi
import dev.glinesb.openmacros.domain.FoodItem
import dev.glinesb.openmacros.domain.FoodMerge
import dev.glinesb.openmacros.domain.FoodSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.IOException

data class SearchOutcome(val items: List<FoodItem>, val problems: List<String>)

class FoodRepository(
    private val db: AppDatabase,
    private val off: OpenFoodFactsApi,
    private val usda: UsdaApi,
    private val keys: SecureKeys,
) {
    private val foods = db.foods()

    val recents = foods.recents()
    val favorites = foods.favorites()
    val customFoods = foods.customFoods()

    fun observe(id: Long) = foods.observe(id)
    suspend fun byId(id: Long) = foods.byId(id)

    /** Local matches first, then Open Food Facts and USDA in parallel, merged and de-duplicated. */
    suspend fun search(query: String): SearchOutcome = coroutineScope {
        val q = query.trim()
        if (q.isEmpty()) return@coroutineScope SearchOutcome(emptyList(), emptyList())
        val key = keys.usdaKey
        val offJob = async { runCatching { off.search(q) } }
        val usdaJob = async { runCatching { usda.search(q, key ?: UsdaApi.DEMO_KEY) } }
        val local = foods.searchLocal(q).map { it.toItem() }
        val offRes = offJob.await()
        val usdaRes = usdaJob.await()
        val problems = buildList {
            offRes.exceptionOrNull()?.let { add("Open Food Facts: ${describe(it)}") }
            usdaRes.exceptionOrNull()?.let {
                val limited = it is HttpStatusException && (it.code == 429 || it.code == 403)
                add(
                    if (limited && key == null) "USDA: the shared DEMO_KEY hit its rate limit. Add your own free key in Settings."
                    else "USDA: ${describe(it)}",
                )
            }
        }
        SearchOutcome(
            // Local foods stay pinned on top; remote duplicates of them are dropped.
            FoodMerge.dedupe(local + FoodMerge.merge(q, listOf(usdaRes.getOrDefault(emptyList()), offRes.getOrDefault(emptyList())))),
            problems,
        )
    }

    private fun describe(t: Throwable) = when (t) {
        is HttpStatusException -> "server returned ${t.code}"
        is IOException -> "network error (offline?)"
        else -> t.javaClass.simpleName
    }

    /** Barcode lookup: our own foods first (custom foods with that barcode), then Open Food Facts. */
    suspend fun lookupBarcode(barcode: String): FoodEntity? {
        val code = barcode.filter { it.isDigit() }
        foods.byBarcode(code)?.let { return it }
        val item = off.byBarcode(code) ?: return null
        return foods.byId(cache(item))
    }

    /** Stores (or refreshes) a remote item so it can be logged, favourited and shown in Recent. Returns its id. */
    suspend fun cache(item: FoodItem): Long {
        val existing = foods.bySource(item.source.name, item.sourceId)
        if (existing != null) {
            if (item.source != FoodSource.CUSTOM) {
                foods.update(FoodEntity.from(item).copy(id = existing.id, isFavorite = existing.isFavorite,
                    lastUsedAt = existing.lastUsedAt, useCount = existing.useCount))
            }
            return existing.id
        }
        return foods.insert(FoodEntity.from(item))
    }

    suspend fun saveCustom(food: FoodEntity): Long =
        if (food.id == 0L) foods.insert(food.copy(source = FoodSource.CUSTOM.name, sourceId = java.util.UUID.randomUUID().toString()))
        else { foods.update(food); food.id }

    suspend fun setFavorite(id: Long, favorite: Boolean) = foods.setFavorite(id, favorite)
    suspend fun delete(food: FoodEntity) = db.withTransaction {
        db.log().unlinkFood(food.id)
        foods.delete(food)
    }
}

class LogRepository(private val db: AppDatabase) {
    private val log = db.log()
    fun forDay(day: Long) = log.forDay(day)
    suspend fun byId(id: Long) = log.byId(id)

    suspend fun add(entry: LogEntryEntity) = db.withTransaction {
        val id = log.insert(entry)
        entry.foodId?.let { db.foods().markUsed(it, System.currentTimeMillis()) }
        id
    }

    suspend fun update(entry: LogEntryEntity) = log.update(entry)
    suspend fun delete(entry: LogEntryEntity) = log.delete(entry)
}

class WeightRepository(db: AppDatabase) {
    private val weights = db.weights()
    val all = weights.observeAll()
    suspend fun log(epochDay: Long, kg: Double) = weights.upsert(WeightEntity(epochDay = epochDay, weightKg = kg))
    suspend fun delete(entry: WeightEntity) = weights.delete(entry)
    suspend fun forDay(day: Long) = weights.forDay(day)
}
