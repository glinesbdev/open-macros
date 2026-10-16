package dev.glinesb.openmacros.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dev.glinesb.openmacros.domain.ActivityLevel
import dev.glinesb.openmacros.domain.GoalType
import dev.glinesb.openmacros.domain.HeightUnit
import dev.glinesb.openmacros.domain.MacroGrams
import dev.glinesb.openmacros.domain.MacroSplit
import dev.glinesb.openmacros.domain.Profile
import dev.glinesb.openmacros.domain.Sex
import dev.glinesb.openmacros.domain.Tdee
import dev.glinesb.openmacros.domain.WeightUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/** Non-secret preferences. The profile is null until onboarding is finished. */
@Serializable
data class AppSettings(
    val profile: Profile? = null,
    val macroSplit: MacroSplit = MacroSplit.DEFAULT,
    /** Optional manual calorie target that overrides the computed one. */
    val calorieOverride: Int? = null,
    val weightUnit: WeightUnit = WeightUnit.LB,
    val heightUnit: HeightUnit = HeightUnit.FT_IN,
) {
    val computedCalories: Int? get() = profile?.let(Tdee::calorieTarget)
    val calorieTarget: Int get() = calorieOverride ?: computedCalories ?: 2000
    val macroTargets: MacroGrams get() = macroSplit.grams(calorieTarget)
}

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        write(next)
        _state.value = next
    }

    private fun read(): AppSettings {
        val profile = if (prefs.contains(K_SEX)) runCatching {
            Profile(
                sex = Sex.valueOf(prefs.getString(K_SEX, null)!!),
                ageYears = prefs.getInt(K_AGE, 30),
                heightCm = prefs.getFloat(K_HEIGHT, 170f).toDouble(),
                weightKg = prefs.getFloat(K_WEIGHT, 70f).toDouble(),
                activity = ActivityLevel.valueOf(prefs.getString(K_ACTIVITY, ActivityLevel.LIGHT.name)!!),
                goal = GoalType.valueOf(prefs.getString(K_GOAL, GoalType.MAINTAIN.name)!!),
                rateKgPerWeek = prefs.getFloat(K_RATE, 0f).toDouble(),
            )
        }.getOrNull() else null
        return AppSettings(
            profile = profile,
            macroSplit = MacroSplit(
                prefs.getInt(K_P, MacroSplit.DEFAULT.proteinPct),
                prefs.getInt(K_C, MacroSplit.DEFAULT.carbsPct),
                prefs.getInt(K_F, MacroSplit.DEFAULT.fatPct),
            ).takeIf { it.isValid } ?: MacroSplit.DEFAULT,
            calorieOverride = prefs.getInt(K_OVERRIDE, -1).takeIf { it > 0 },
            weightUnit = runCatching { WeightUnit.valueOf(prefs.getString(K_WUNIT, null)!!) }.getOrDefault(WeightUnit.LB),
            heightUnit = runCatching { HeightUnit.valueOf(prefs.getString(K_HUNIT, null)!!) }.getOrDefault(HeightUnit.FT_IN),
        )
    }

    private fun write(s: AppSettings) {
        prefs.edit().apply {
            val p = s.profile
            if (p == null) {
                listOf(K_SEX, K_AGE, K_HEIGHT, K_WEIGHT, K_ACTIVITY, K_GOAL, K_RATE).forEach(::remove)
            } else {
                putString(K_SEX, p.sex.name)
                putInt(K_AGE, p.ageYears)
                putFloat(K_HEIGHT, p.heightCm.toFloat())
                putFloat(K_WEIGHT, p.weightKg.toFloat())
                putString(K_ACTIVITY, p.activity.name)
                putString(K_GOAL, p.goal.name)
                putFloat(K_RATE, p.rateKgPerWeek.toFloat())
            }
            putInt(K_P, s.macroSplit.proteinPct)
            putInt(K_C, s.macroSplit.carbsPct)
            putInt(K_F, s.macroSplit.fatPct)
            putInt(K_OVERRIDE, s.calorieOverride ?: -1)
            putString(K_WUNIT, s.weightUnit.name)
            putString(K_HUNIT, s.heightUnit.name)
        }.apply()
    }

    private companion object {
        const val K_SEX = "sex"; const val K_AGE = "age"; const val K_HEIGHT = "height_cm"
        const val K_WEIGHT = "weight_kg"; const val K_ACTIVITY = "activity"; const val K_GOAL = "goal"
        const val K_RATE = "rate_kg_week"; const val K_P = "protein_pct"; const val K_C = "carbs_pct"
        const val K_F = "fat_pct"; const val K_OVERRIDE = "calorie_override"
        const val K_WUNIT = "weight_unit"; const val K_HUNIT = "height_unit"
    }
}

/**
 * The user's own API keys (USDA FoodData Central / api.data.gov, and xAI), encrypted at rest with
 * EncryptedSharedPreferences (AES-256-GCM values, key wrapped by an Android Keystore master key).
 * Never logged, never exported, and app backup is disabled.
 */
class SecureKeys(context: Context) {
    private val prefs: SharedPreferences? = try {
        EncryptedSharedPreferences.create(
            context.applicationContext,
            "secure_keys",
            MasterKey.Builder(context.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        // Keystore trouble: refuse to store the key rather than fall back to plaintext.
        Log.w("SecureKeys", "Encrypted storage unavailable: ${e.javaClass.simpleName}")
        null
    }

    private val _hasUsdaKey = MutableStateFlow(usdaKey != null)
    val hasUsdaKey: StateFlow<Boolean> = _hasUsdaKey.asStateFlow()

    val isAvailable: Boolean get() = prefs != null

    var usdaKey: String?
        get() = prefs?.getString(K_USDA, null)?.takeIf { it.isNotBlank() }
        set(v) {
            prefs?.edit()?.apply { if (v.isNullOrBlank()) remove(K_USDA) else putString(K_USDA, v.trim()) }?.apply()
            _hasUsdaKey.value = !v.isNullOrBlank() && prefs != null
        }

    private val _hasXaiKey = MutableStateFlow(xaiKey != null)
    val hasXaiKey: StateFlow<Boolean> = _hasXaiKey.asStateFlow()

    /** The user's own xAI API key for optional photo logging (billed to their xAI account). */
    var xaiKey: String?
        get() = prefs?.getString(K_XAI, null)?.takeIf { it.isNotBlank() }
        set(v) {
            prefs?.edit()?.apply { if (v.isNullOrBlank()) remove(K_XAI) else putString(K_XAI, v.trim()) }?.apply()
            _hasXaiKey.value = !v.isNullOrBlank() && prefs != null
        }

    private companion object {
        const val K_USDA = "usda_api_key"
        const val K_XAI = "xai_api_key"
    }
}
