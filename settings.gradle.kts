pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
  plugins {
    id("com.android.application") version "8.5.2"
    id("com.android.library") version "8.5.2"
    id("org.jetbrains.kotlin.android") version "2.0.20"
    id("org.jetbrains.kotlin.jvm") version "2.0.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20"
    id("org.jetbrains.kotlinx.kover") version "0.7.6"
    id("com.diffplug.spotless") version "6.25.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.6"
    id("com.google.gms.google-services") version "4.4.2"
    id("com.google.firebase.crashlytics") version "2.9.9"
    id("app.cash.licensee") version "1.7.0"
    id("org.cyclonedx.bom") version "1.9.0"
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "QWeld"

include(
  ":app-android",
  ":core-model",
  ":core-domain",
  ":core-data",
  ":feature-exam",
  ":feature-auth",
)
