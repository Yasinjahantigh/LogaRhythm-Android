plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.hilt.android)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.musicplayer.qpmwzx"
    minSdk = 26
    targetSdk = 36
    versionCode = 4
    versionName = "1.3.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Release signing is opt-in: CI passes KEYSTORE_PATH / STORE_PASSWORD / KEY_PASSWORD (and optionally
  // KEY_ALIAS). Without them the release build is signed with the debug key so a local
  // `assembleRelease` always produces an installable APK.
  val releaseStorePassword: String? = System.getenv("STORE_PASSWORD")
  signingConfigs {
    if (!releaseStorePassword.isNullOrBlank()) {
      create("release") {
        storeFile = file(System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks")
        storePassword = releaseStorePassword
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD") ?: releaseStorePassword
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      // R8 + resource shrinking: without it the APK ships the whole Compose / Material-Icons
      // extended library un-optimised, which is the single biggest reason release builds felt
      // heavy (slow cold start, janky first scroll, big install).
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
    }
    // Use Android Gradle Plugin's standard per-user debug keystore.
    debug { }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
  }
  // The app ships English and Persian only; this drops every other locale bundled by libraries.
  androidResources {
    localeFilters += listOf("en", "fa")
  }
  packaging {
    resources {
      excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.version", "/kotlin/**", "DebugProbesKt.bin")
    }
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)

  // Hilt Dependency Injection
  implementation(libs.hilt.android)
  "ksp"(libs.hilt.android.compiler)
  implementation(libs.androidx.hilt.navigation.compose)

  // Room Database
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  "ksp"(libs.androidx.room.compiler)

  // DataStore Preferences
  implementation(libs.androidx.datastore.preferences)

  // Media3 Playback & Session
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.session)

  // Coil Image Loading
  implementation(libs.coil.compose)

  implementation(libs.kotlinx.coroutines.android)

  // Ships the baseline profile (src/main/baseline-prof.txt) to ART at install time.
  implementation(libs.androidx.profileinstaller)

  // Glance AppWidget
  implementation(libs.androidx.glance)
  implementation(libs.androidx.glance.appwidget)
  implementation(libs.androidx.glance.material3)

  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}

// ---------------------------------------------------------------------------------------------
// Persian UI font (Vazirmatn, SIL Open Font License 1.1 - https://github.com/rastikerdar/vazirmatn)
//
// The files are fetched once into src/main/assets/fonts on the first build and then reused, so
// the app ships with a proper Persian typeface without committing binaries to the repository.
// It is strictly best-effort: if the download fails (offline CI, blocked host) the build still
// succeeds and the app falls back to Poppins + the system's Arabic-script font. You can also drop
// Vazirmatn-{Light,Regular,Medium,SemiBold,Bold}.ttf into src/main/assets/fonts yourself.
// ---------------------------------------------------------------------------------------------
val downloadPersianFont = tasks.register("downloadPersianFont") {
  val fontsDir = layout.projectDirectory.dir("src/main/assets/fonts").asFile
  doLast {
    fontsDir.mkdirs()
    val version = "v33.003"
    listOf("Light", "Regular", "Medium", "SemiBold", "Bold").forEach { weight ->
      val target = java.io.File(fontsDir, "Vazirmatn-$weight.ttf")
      fun isFont(file: java.io.File): Boolean {
        if (!file.exists() || file.length() < 20_000L) return false
        val head = ByteArray(4)
        file.inputStream().use { it.read(head) }
        val tag = String(head, Charsets.ISO_8859_1)
        return (head[0].toInt() == 0 && head[1].toInt() == 1 && head[2].toInt() == 0 && head[3].toInt() == 0) ||
          tag == "OTTO" || tag == "true"
      }
      if (isFont(target)) return@forEach
      val sources = listOf(
        "https://cdn.jsdelivr.net/gh/rastikerdar/vazirmatn@$version/fonts/ttf/Vazirmatn-$weight.ttf",
        "https://raw.githubusercontent.com/rastikerdar/vazirmatn/$version/fonts/ttf/Vazirmatn-$weight.ttf"
      )
      for (source in sources) {
        try {
          val connection = java.net.URL(source).openConnection()
          connection.connectTimeout = 8_000
          connection.readTimeout = 15_000
          connection.getInputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
          if (isFont(target)) break
          target.delete()
        } catch (e: Exception) {
          target.delete()
        }
      }
      if (!isFont(target)) {
        logger.warn("Persian font Vazirmatn-$weight.ttf was not downloaded; the app will use the system fallback for Persian.")
      }
    }
  }
}

tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(downloadPersianFont) }
