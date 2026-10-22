import java.io.ByteArrayOutputStream
import java.io.File

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlinAndroid)
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
    Pair(10301, "1.3.1")
  }
}

val (appVersionCode, appVersionName) = getVersionInfo()

android {
  namespace = "com.yasinjahantigh.logarhythm"
  compileSdk = 36
  buildToolsVersion = "36.1.0"

  defaultConfig {
    applicationId = "com.yasinjahantigh.logarhythm"
    minSdk = 26
    targetSdk = 36
    versionCode = appVersionCode
    versionName = appVersionName
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
      signingConfig = signingConfigs.findByName("release")
    }
    debug {
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-debug"
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlinOptions {
    jvmTarget = "11"
  }
  buildFeatures {
    compose = true
  }
  androidResources {
    localeFilters += listOf("en", "fa")
  }
  bundle {
    language {
      enableSplit = false
    }
  }
  packaging {
    resources {
      excludes += listOf(
        "/META-INF/{AL2.0,LGPL2.1}",
        "/META-INF/LICENSE",
        "/META-INF/LICENSE.txt",
        "/META-INF/NOTICE",
        "/META-INF/NOTICE.txt",
        "/META-INF/DEPENDENCIES",
        "/META-INF/*.version",
        "/kotlin/**",
        "DebugProbesKt.bin"
      )
    }
    // yt-dlp runs its bundled Python and ffmpeg from nativeLibraryDir, so the .so payload
    // must be extracted at install time instead of loaded straight from the APK.
    jniLibs { useLegacyPackaging = true }
  }
  splits {
    abi {
      isEnable = true
      reset()
      include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
      isUniversalApk = false
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
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)

  implementation(libs.hilt.android)
  "ksp"(libs.hilt.android.compiler)
  implementation(libs.androidx.hilt.navigation.compose)

  implementation(libs.androidx.room.runtime)
  "ksp"(libs.androidx.room.compiler)

  implementation(libs.androidx.datastore.preferences)

  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.session)

  implementation(libs.coil.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.androidx.profileinstaller)

  implementation(libs.androidx.glance)
  implementation(libs.androidx.glance.appwidget)

  implementation(libs.androidx.work.runtime)
  implementation(libs.androidx.hilt.work)
  "ksp"(libs.androidx.hilt.compiler)
  implementation(libs.okhttp)
  implementation(libs.youtubedl.android)

  testImplementation(libs.androidx.core)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)

  debugImplementation(libs.androidx.compose.ui.tooling)
}
