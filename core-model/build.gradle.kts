plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
}

android {
  namespace = "com.qweld.core.model"
  compileSdk = 35
  buildToolsVersion = "35.0.0"

  defaultConfig {
    minSdk = 24
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    consumerProguardFiles("consumer-rules.pro")
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  kotlinOptions { jvmTarget = "21" }
}

kotlin {
  jvmToolchain(21)
}

dependencies { testImplementation("junit:junit:4.13.2") }
