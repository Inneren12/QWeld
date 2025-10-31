plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.kapt")
}

android {
  namespace = "com.qweld.core.data"
  compileSdk = 36

  defaultConfig {
    minSdk = 24
    consumerProguardFiles("consumer-rules.pro")
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  kotlinOptions { jvmTarget = "21" }
}

dependencies {
  implementation(project(":core-domain"))

  implementation("androidx.room:room-runtime:2.6.1")
  implementation("androidx.room:room-ktx:2.6.1")
  kapt("androidx.room:room-compiler:2.6.1")

  implementation("androidx.datastore:datastore-preferences:1.1.1")

  testImplementation(kotlin("test"))
  testImplementation(project(":core-domain"))
  testImplementation("androidx.test:core:1.6.1")
  testImplementation("androidx.room:room-testing:2.6.1")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
