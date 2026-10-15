import java.io.File
import java.io.ByteArrayOutputStream
import java.net.URI

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.hilt.android)
}

fun getVersionInfo(): Pair<Int, String> {
  var tag = System.getenv("GIT_TAG") ?: ""
  if (tag.isBlank()) {
    try {
      val stdout = ByteArrayOutputStream()
      providers.exec {
        commandLine("git", "describe", "--tags", "--always")
        standardOutput = stdout
        isIgnoreExitValue = true
      }
      tag = stdout.toString().trim()
    } catch (_: Exception) {}
  }

  val versionPattern = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-.*)?$""")
  val match = versionPattern.matchEntire(tag)
  return if (match != null) {
    val (major, minor, patch) = match.destructured
    val code = major.toInt() * 10000 + minor.toInt() * 100 + patch.toInt()
    val name = "$major.$minor.$patch"
    Pair(code, name)
  } else {
    Pair(10300, "1.3.0")
  }
}

val (appVersionCode, appVersionName) = getVersionInfo()

android {
  namespace = "com.example"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.aistudio.musicplayer.qpmwzx"
    minSdk = 26
    targetSdk = 35
    versionCode = appVersionCode
    versionName = appVersionName

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  val ksPath = System.getenv("KEYSTORE_PATH") ?: findProperty("KEYSTORE_PATH") as? String
  val ksPassword = System.getenv("KEYSTORE_PASSWORD") ?: System.getenv("STORE_PASSWORD") ?: findProperty("KEYSTORE_PASSWORD") as? String ?: findProperty("STORE_PASSWORD") as? String
  val kAlias = System.getenv("KEY_ALIAS") ?: findProperty("KEY_ALIAS") as? String
  val kPassword = System.getenv("KEY_PASSWORD") ?: findProperty("KEY_PASSWORD") as? String ?: ksPassword

  val hasSigningSecrets = !ksPath.isNullOrBlank() && !ksPassword.isNullOrBlank() && !kAlias.isNullOrBlank() && !kPassword.isNullOrBlank()
  val requireSigning = System.getenv("REQUIRE_RELEASE_SIGNING") == "true"

  if (requireSigning && !hasSigningSecrets) {
    throw GradleException("Release build requires KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, and KEY_PASSWORD environment variables or Gradle properties.")
  }

  signingConfigs {
    if (hasSigningSecrets) {
      create("release") {
        storeFile = File(ksPath!!)
        storePassword = ksPassword!!
        keyAlias = kAlias!!
        keyPassword = kPassword!!
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
    }
    debug { }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
  }
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

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
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
}

val downloadPersianFont = tasks.register("downloadPersianFont") {
  val fontsDir = layout.projectDirectory.dir("src/main/assets/fonts").asFile
  doLast {
    fontsDir.mkdirs()
    val version = "v33.003"
    listOf("Light", "Regular", "Medium", "SemiBold", "Bold").forEach { weight ->
      val target = File(fontsDir, "Vazirmatn-$weight.ttf")
      fun isFont(file: File): Boolean {
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
          val connection = URI(source).toURL().openConnection()
          connection.connectTimeout = 8_000
          connection.readTimeout = 15_000
          connection.getInputStream().use { input ->
            target.outputStream().use { output ->
              input.copyTo(output)
            }
          }
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
