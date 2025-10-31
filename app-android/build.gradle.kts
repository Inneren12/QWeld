plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "com.qweld.app"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.qweld.app"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "0.1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    resourceConfigurations += setOf("en", "ru")
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  kotlinOptions { jvmTarget = "21" }
}

dependencies {
  implementation(project(":core-model"))
  implementation(project(":core-domain"))
  implementation(project(":core-data"))
  implementation(project(":feature-exam"))

  implementation("androidx.core:core-ktx:1.13.1")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
  implementation("androidx.activity:activity-compose:1.9.3")
  implementation("androidx.navigation:navigation-compose:2.8.3")
  implementation("androidx.compose.ui:ui:1.7.1")
  implementation("androidx.compose.ui:ui-tooling-preview:1.7.1")
  implementation("androidx.compose.foundation:foundation:1.7.1")
  implementation("androidx.compose.material3:material3:1.3.1")
  implementation("androidx.compose.material3:material3-window-size-class:1.3.1")
  implementation("com.google.android.material:material:1.12.0")
  implementation("com.jakewharton.timber:timber:5.0.1")

  debugImplementation("androidx.compose.ui:ui-tooling:1.7.1")
  debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.1")

  androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.7.1")
  androidTestImplementation("androidx.test.ext:junit:1.1.5")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

  testImplementation("junit:junit:4.13.2")
}
