pluginManagement {
  resolutionStrategy {
    eachPlugin {
      if (requested.id.id == "com.google.devtools.ksp") {
        useModule("com.google.devtools.ksp:symbol-processing-gradle-plugin:${requested.version}")
      }
    }
  }
  repositories {
    // Opt-in mirrors for networks where Google Maven is blocked: logarhythm.mirrors=true
    val mirrors = java.util.Properties().apply {
      val file = java.io.File(settingsDir, "local.properties")
      if (file.isFile) file.inputStream().use { load(it) }
    }.getProperty("logarhythm.mirrors") == "true"
    if (mirrors) {
      maven("https://maven.aliyun.com/repository/google")
      maven("https://maven.aliyun.com/repository/public")
      maven("https://maven.aliyun.com/repository/gradle-plugin")
    }
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
  repositories {
    val mirrors = java.util.Properties().apply {
      val file = java.io.File(settingsDir, "local.properties")
      if (file.isFile) file.inputStream().use { load(it) }
    }.getProperty("logarhythm.mirrors") == "true"
    if (mirrors) {
      maven("https://maven.aliyun.com/repository/google")
      maven("https://maven.aliyun.com/repository/public")
    }
    google()
    mavenCentral()
  }
}

rootProject.name = "LogaRhythm"

include(":app")
