import org.cyclonedx.gradle.CycloneDxTask
import org.gradle.api.attributes.Attribute

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
  id("com.google.gms.google-services")
  id("com.google.firebase.crashlytics")
  id("app.cash.licensee")
  id("org.cyclonedx.bom")
}

android {
  namespace = "com.qweld.app"
  compileSdk = 36

  val enableAnalyticsDebug =
    providers.gradleProperty("enableAnalyticsDebug").map { it.toBoolean() }.getOrElse(false)

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
    debug { buildConfigField("Boolean", "ENABLE_ANALYTICS", enableAnalyticsDebug.toString()) }
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      buildConfigField("Boolean", "ENABLE_ANALYTICS", "true")
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

afterEvaluate {
  configurations.findByName("releaseCompileClasspath")?.attributes?.attribute(
    Attribute.of("artifactType", String::class.java),
    "android-classes-jar",
  )
}

dependencies {
  implementation(project(":core-model"))
  implementation(project(":core-domain"))
  implementation(project(":core-data"))
  implementation(project(":feature-exam"))
  implementation(project(":feature-auth"))

  implementation("androidx.core:core-ktx:1.13.1")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
  implementation("androidx.activity:activity-compose:1.9.3")
  implementation("androidx.navigation:navigation-compose:2.8.3")
  implementation("androidx.compose.ui:ui:1.7.1")
  implementation("androidx.compose.ui:ui-tooling-preview:1.7.1")
  implementation("androidx.compose.foundation:foundation:1.7.1")
  implementation("androidx.compose.material3:material3:1.3.1")
  implementation("androidx.compose.material3:material3-window-size-class:1.3.1")
  implementation("androidx.compose.material:material-icons-extended:1.7.1")
  implementation("com.google.android.material:material:1.12.0")
  implementation("com.jakewharton.timber:timber:5.0.1")
  implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
  implementation("com.google.firebase:firebase-auth-ktx")
  implementation("com.google.android.gms:play-services-auth:21.2.0")
  implementation("com.google.firebase:firebase-analytics-ktx")
  implementation("com.google.firebase:firebase-crashlytics-ktx")

  debugImplementation("androidx.compose.ui:ui-tooling:1.7.1")
  debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.1")

  androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.7.1")
  androidTestImplementation("androidx.test.ext:junit:1.1.5")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

  testImplementation("junit:junit:4.13.2")
}

licensee {
  allow("Apache-2.0")
  allow("MIT")
  allow("BSD-2-Clause")
  allow("BSD-3-Clause")
  allow("ISC")
  allow("MPL-2.0")

  allowUrl("https://developer.android.com/studio/terms.html")
  allowUrl("https://developer.android.com/guide/playcore/license")
  allowUrl("https://developer.android.com/google/play/integrity/overview#tos")
  allowUrl("https://source.android.com/license")
}

tasks.named<CycloneDxTask>("cyclonedxBom") {
  includeConfigs.set(listOf("releaseCompileClasspath"))
  outputFormat.set("json")
  schemaVersion.set("1.5")
  projectType.set("application")
  destination.set(layout.buildDirectory.dir("reports/bom").map { it.asFile })
  skipProjects.set(emptyList())
  skipProjects.addAll("core-model", "core-data", "feature-exam", "feature-auth")
}
