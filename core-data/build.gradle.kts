plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.kapt")
  id("org.jetbrains.kotlin.plugin.serialization")
}

android {
  namespace = "com.qweld.core.data"
  compileSdk = 36

  defaultConfig {
    minSdk = 24
    consumerProguardFiles("consumer-rules.pro")
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildFeatures { buildConfig = true }

  buildTypes {
    getByName("debug") { buildConfigField("Boolean", "ENABLE_ANALYTICS", "false") }
    getByName("release") { buildConfigField("Boolean", "ENABLE_ANALYTICS", "true") }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  kotlinOptions { jvmTarget = "21" }
}

dependencies {
  implementation(project(":core-domain"))

    api("androidx.room:room-runtime:2.6.1")
    api("androidx.room:room-ktx:2.6.1")
  kapt("androidx.room:room-compiler:2.6.1")

  implementation("androidx.datastore:datastore-preferences:1.1.1")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
  implementation("com.jakewharton.timber:timber:5.0.1")
  implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
  implementation("com.google.firebase:firebase-analytics-ktx")

  testImplementation(kotlin("test"))
  testImplementation(project(":core-domain"))
  testImplementation("androidx.test:core:1.6.1")
  testImplementation("androidx.room:room-testing:2.6.1")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
  testImplementation(project(":feature-exam"))
}
