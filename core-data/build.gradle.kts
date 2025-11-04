plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.kapt")
  id("org.jetbrains.kotlin.plugin.serialization")
}

android {
  namespace = "com.qweld.core.data"
  compileSdk = 35
  buildToolsVersion = "35.0.0"

  defaultConfig {
    minSdk = 24
    consumerProguardFiles("consumer-rules.pro")
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildFeatures { buildConfig = true }

  sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")

  buildTypes {
    getByName("debug") { buildConfigField("Boolean", "ENABLE_ANALYTICS", "false") }
    getByName("release") { buildConfigField("Boolean", "ENABLE_ANALYTICS", "true") }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  kotlinOptions { jvmTarget = "21" }

  testOptions {
    unitTests.apply {
      isIncludeAndroidResources = true
      isReturnDefaultValues = true
    }
  }
}

kotlin {
  jvmToolchain(21)
}

kapt {
  arguments {
    arg("room.schemaLocation", "$projectDir/schemas")
  }
}

dependencies {
  implementation(project(":core-domain"))
  implementation(project(":core-common"))

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
  testImplementation("androidx.test:core-ktx:1.5.0")
  testImplementation("androidx.room:room-testing:2.6.1")
  testImplementation("org.robolectric:robolectric:4.12.2")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
  testImplementation(project(":feature-exam"))

  androidTestImplementation("androidx.room:room-testing:2.6.1")
  androidTestImplementation("androidx.test:core-ktx:1.5.0")
  androidTestImplementation("androidx.test.ext:junit:1.1.5")
  androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
