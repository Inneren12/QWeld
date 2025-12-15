plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.serialization")
  id("com.google.devtools.ksp")
}

android {
  namespace = "com.qweld.core.data"
  compileSdk = 35

  defaultConfig {
    minSdk = 26
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

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
  implementation(project(":core-domain"))
  implementation(project(":core-common"))

    api("androidx.room:room-runtime:2.6.1")
    api("androidx.room:room-ktx:2.6.1")
  ksp("androidx.room:room-compiler:2.6.1")

    implementation("com.jakewharton.timber:timber:5.0.1")

  implementation("androidx.datastore:datastore-preferences:1.1.1")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
  implementation("com.jakewharton.timber:timber:5.0.1")
  implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
  implementation("com.google.firebase:firebase-analytics-ktx")
  //implementation("com.google.firebase:firebase-firestore-ktx")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

  testImplementation(kotlin("test"))
  testImplementation(project(":core-domain"))
  testImplementation("androidx.test:core-ktx:1.5.0")
  testImplementation("androidx.room:room-testing:2.6.1")
  testImplementation("org.robolectric:robolectric:4.12.2")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
  testImplementation(project(":feature-exam"))

    api("com.google.firebase:firebase-firestore-ktx")

  androidTestImplementation("androidx.room:room-testing:2.6.1")
  androidTestImplementation("androidx.test:core-ktx:1.5.0")
  androidTestImplementation("androidx.test.ext:junit:1.1.5")
  androidTestImplementation("androidx.test:runner:1.5.2")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
  androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    implementation("com.jakewharton.timber:timber:5.0.1")
}
