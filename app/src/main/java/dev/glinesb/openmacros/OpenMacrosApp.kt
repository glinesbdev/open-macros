package dev.glinesb.openmacros

import android.app.Application
import android.content.Context
import dev.glinesb.openmacros.data.FoodRepository
import dev.glinesb.openmacros.data.LogRepository
import dev.glinesb.openmacros.data.SecureKeys
import dev.glinesb.openmacros.data.SettingsStore
import dev.glinesb.openmacros.data.TrainingRepository
import dev.glinesb.openmacros.data.WeightRepository
import dev.glinesb.openmacros.data.backup.BackupManager
import dev.glinesb.openmacros.data.db.AppDatabase
import dev.glinesb.openmacros.data.remote.OpenFoodFactsApi
import dev.glinesb.openmacros.data.remote.UsdaApi
import dev.glinesb.openmacros.photo.FoodPhotoEstimator
import dev.glinesb.openmacros.photo.XaiFoodPhotoEstimator

class OpenMacrosApp : Application() {
    val container by lazy { AppContainer(this) }
}

/** Hand-rolled dependency container; the app is small enough not to need a DI framework. */
class AppContainer(context: Context) {
    val db = AppDatabase.create(context)
    val settings = SettingsStore(context)
    val keys = SecureKeys(context)
    val foods = FoodRepository(db, OpenFoodFactsApi(), UsdaApi(), keys)
    val log = LogRepository(db)
    val weights = WeightRepository(db)
    val training = TrainingRepository(db, settings)
    val backup = BackupManager(db, settings, context.contentResolver, training)

    /** Optional photo logging with the user's own xAI key (available only once a key is saved). */
    val photoEstimator: FoodPhotoEstimator = XaiFoodPhotoEstimator(keys)
}

val Context.container: AppContainer get() = (applicationContext as OpenMacrosApp).container
