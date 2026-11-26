import java.io.ByteArrayOutputStream
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlinAndroid)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.hilt.android)
}

abstract class StripInstallPermissionTask : DefaultTask() {
  @get:InputFile
  abstract val mergedManifest: RegularFileProperty

  @get:OutputFile
  abstract val updatedManifest: RegularFileProperty

  @get:Input
  abstract val stripInstallPermission: Property<Boolean>

  @TaskAction
  fun taskAction() {
    val text = mergedManifest.get().asFile.readText()
    val result = if (stripInstallPermission.get()) {
      text.replace(
        Regex("""<uses-permission\b[^>]*android:name\s*=\s*"android\.permission\.REQUEST_INSTALL_PACKAGES"[^>]*(?:/>|>\s*</uses-permission>)\s*"""),
        ""
      )
    } else {
      text
    }
    updatedManifest.get().asFile.writeText(result)
  }
}

fun getVersionInfo(): Pair<Int, String> {
  var tag = System.getenv("GIT_TAG") ?: ""
  if (tag.isBlank()) {
    try {
      tag = providers.exec {
        commandLine("git", "describe", "--tags", "--always")
        isIgnoreExitValue = true
      }.standardOutput.asText.get().trim()
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
    Pair(20200, "2.2.0")
  }
}

val (appVersionCode, appVersionName) = getVersionInfo()

val distributionChannel = (findProperty("distributionChannel") as? String)
  ?.trim()
  ?.lowercase()
  ?.takeIf { it in setOf("github", "myket") }
  ?: "github"
val selfUpdateEnabled = distributionChannel != "myket"
val enableDownloader = (findProperty("enableDownloader") as? String)?.trim()?.toBooleanStrictOrNull() ?: true
val enableYoutubeDownloader = (findProperty("enableYoutubeDownloader") as? String)?.trim()?.toBooleanStrictOrNull() ?: true

// Anonymous telemetry & release radar endpoint; defaults to production worker if not overridden.
val installEndpoint = System.getenv("INSTALL_ENDPOINT")?.trim()?.takeIf { it.isNotEmpty() }
  ?: (findProperty("INSTALL_ENDPOINT") as? String)?.trim()?.takeIf { it.isNotEmpty() }
  ?: "https://logarhythm-installs.yasinjahantigh.workers.dev/hit"

android {
  namespace = "com.yasinjahantigh.logarhythm"
  compileSdk = 35
  buildToolsVersion = "35.0.0"

  defaultConfig {
    applicationId = "com.yasinjahantigh.logarhythm"
    minSdk = 26
    targetSdk = 35
    versionCode = appVersionCode
    versionName = appVersionName
    buildConfigField("String", "INSTALL_ENDPOINT", "\"$installEndpoint\"")
    buildConfigField("String", "DISTRIBUTION_CHANNEL", "\"$distributionChannel\"")
    buildConfigField("boolean", "SELF_UPDATE_ENABLED", "$selfUpdateEnabled")
    buildConfigField("boolean", "ENABLE_DOWNLOADER", "$enableDownloader")
    buildConfigField("boolean", "ENABLE_YOUTUBE_DOWNLOADER", "$enableYoutubeDownloader")
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

  val rootDebugKs = file("${rootDir}/debug.keystore")
  signingConfigs {
    if (rootDebugKs.exists()) {
      create("debugConfig") {
        storeFile = rootDebugKs
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
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
      if (rootDebugKs.exists()) {
        signingConfig = signingConfigs.findByName("debugConfig")
      }
    }
  }

  lint {
    abortOnError = true
    checkReleaseBuilds = true
    warningsAsErrors = false
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
    buildConfig = true
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
      isEnable = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
      reset()
      include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
      isUniversalApk = true
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

composeCompiler {
  featureFlags.add(org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag.StrongSkipping)
}

androidComponents {
  onVariants { variant ->
    val taskProvider = tasks.register<StripInstallPermissionTask>(
      "stripInstallPermission${variant.name.replaceFirstChar { it.uppercase() }}"
    ) {
      stripInstallPermission.set(distributionChannel == "myket")
    }
    variant.artifacts.use(taskProvider)
      .wiredWithFiles(
        StripInstallPermissionTask::mergedManifest,
        StripInstallPermissionTask::updatedManifest
      )
      .toTransform(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST)
  }
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
  implementation(libs.androidx.exifinterface)
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
  testImplementation(libs.mockwebserver)

  debugImplementation(libs.androidx.compose.ui.tooling)
}
