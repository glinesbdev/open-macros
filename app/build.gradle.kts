import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// ---- Stable debug signing (see README "Signing") ----
// CI decodes the SIGNING_KEYSTORE_B64 secret to a temp file and sets SIGNING_KEYSTORE_PATH plus the
// password/alias env vars. Locally you can point local.properties (gitignored) at a keystore kept outside
// the repo. With neither, the default Android debug key is used, so builds never fail for lack of secrets.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
fun signingValue(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: localProps.getProperty(prop)?.takeIf { it.isNotBlank() }
val stableKeystore: File? = signingValue("SIGNING_KEYSTORE_PATH", "signing.storeFile")?.let { File(it) }?.takeIf { it.isFile }
val stableStorePassword = signingValue("SIGNING_STORE_PASSWORD", "signing.storePassword")
val stableKeyAlias = signingValue("SIGNING_KEY_ALIAS", "signing.keyAlias")
val stableKeyPassword = signingValue("SIGNING_KEY_PASSWORD", "signing.keyPassword")
val useStableSigning = stableKeystore != null && stableStorePassword != null && stableKeyAlias != null && stableKeyPassword != null
logger.lifecycle(if (useStableSigning) "Debug signing: stable key" else "Debug signing: default debug key")

// CI builds take their versionCode from the run number so every new build installs as an update.
val ciRunNumber: Int? = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()


android {
    namespace = "dev.glinesb.openmacros"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.glinesb.openmacros"
        minSdk = 29
        targetSdk = 36
        // Local builds stay at 2; CI uses 100 + run number (always higher, always increasing).
        versionCode = ciRunNumber?.let { 100 + it } ?: 2
        versionName = "0.2.0"
        // Phones (arm) + x86_64 emulators. Drops 32-bit x86 to keep the ML Kit native lib smaller.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }
    signingConfigs {
        if (useStableSigning) create("stableDebug") {
            storeFile = stableKeystore
            storePassword = stableStorePassword
            keyAlias = stableKeyAlias
            keyPassword = stableKeyPassword
        }
    }
    buildTypes {
        getByName("debug") {
            if (useStableSigning) signingConfig = signingConfigs.getByName("stableDebug")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.browser:browser:1.8.0") // Custom Tabs for help links
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    // Local database
    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    // Networking + JSON
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Encrypted storage for the user's USDA API key
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Barcode scanning: CameraX + ML Kit (bundled, on-device model)
    val camerax = "1.4.1"
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    testImplementation("junit:junit:4.13.2")
    // Real SQLite on the JVM for the Room migration test (runs the migration SQL, checks it against app/schemas).
    testImplementation("org.xerial:sqlite-jdbc:3.46.1.3")
}
