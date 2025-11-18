plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
  id("org.jetbrains.kotlin.plugin.serialization")
}

android {
  namespace = "com.qweld.app.feature.exam"
  compileSdk = 35

  defaultConfig {
    minSdk = 24
  }

  buildFeatures { compose = true }

  composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  kotlinOptions { jvmTarget = "21" }
}

kotlin {
  jvmToolchain(21)
}

dependencies {
  implementation(project(":core-domain"))
  implementation(project(":core-data"))
  implementation(project(":core-common"))
  implementation("androidx.core:core-ktx:1.15.0")
  implementation("androidx.activity:activity-compose:1.9.3")
  implementation("androidx.compose.foundation:foundation:1.7.1")
  implementation("androidx.compose.material3:material3:1.3.1")
  implementation("androidx.compose.material:material-icons-extended:1.7.1")
  implementation("androidx.compose.runtime:runtime:1.7.1")
  implementation("androidx.compose.ui:ui:1.7.1")
  implementation("androidx.compose.ui:ui-tooling-preview:1.7.1")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
  implementation("androidx.navigation:navigation-compose:2.8.3")
  implementation("com.jakewharton.timber:timber:5.0.1")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.appcompat:appcompat:1.7.0")
    //implementation(project(":app-android"))

    debugImplementation("androidx.compose.ui:ui-tooling:1.7.1")
  debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.1")

  testImplementation("junit:junit:4.13.2")
  testImplementation("androidx.test:core:1.6.1")
  testImplementation("androidx.room:room-testing:2.6.1")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
  testImplementation(project(":core-data"))

  androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.7.1")
  androidTestImplementation("androidx.test.ext:junit:1.2.1")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
  androidTestImplementation("androidx.navigation:navigation-testing:2.8.3")
}
