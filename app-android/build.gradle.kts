import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.cyclonedx.gradle.CycloneDxTask
import org.gradle.api.GradleException
import org.gradle.api.attributes.Attribute
import org.gradle.language.base.plugins.LifecycleBasePlugin

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("app.cash.licensee")
    id("org.cyclonedx.bom")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.google.devtools.ksp")
}

val hasGoogleServices =
  rootProject.file("app-android/google-services.json").exists() ||
    project.findProperty("withGoogleServices")?.toString() == "true"

if (hasGoogleServices) {
  apply(plugin = "com.google.gms.google-services")
  apply(plugin = "com.google.firebase.crashlytics")
} else {
  logger.lifecycle("CI: google-services.json absent → skipping GMS/Crashlytics")
}

tasks
  .matching { it.name.contains("uploadCrashlyticsMappingFile") }
  .configureEach { onlyIf { hasGoogleServices } }

val autoVersionCode = SimpleDateFormat("yyMMddHH", Locale.US).apply {
  timeZone = TimeZone.getTimeZone("UTC")
}.format(Date()).toInt()
extra["autoVersionCode"] = autoVersionCode

val gitShaProvider =
  providers.gradleProperty("gitSha").orElse(System.getenv("GITHUB_SHA") ?: "local")
val buildTimestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
  timeZone = TimeZone.getTimeZone("UTC")
}.format(Date())

android {
  namespace = "com.qweld.app"
  compileSdk = 35

  val enableAnalyticsDebug =
    providers.gradleProperty("enableAnalyticsDebug").map { it.toBoolean() }.getOrElse(false)

  defaultConfig {
    applicationId = "com.qweld.app"
    minSdk = 26
    targetSdk = 35
    versionCode = extra["autoVersionCode"] as Int
    versionName = "1.0.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    resourceConfigurations += setOf("en", "ru")
    buildConfigField("String", "BUILD_TIME", "\"$buildTimestamp\"")
    buildConfigField("String", "GIT_SHA", "\"${gitShaProvider.get()}\"")
    buildConfigField("boolean", "PREWARM_ENABLED", "true")
    buildConfigField("int", "PREWARM_MAX_CONCURRENCY", "3")
    buildConfigField("long", "PREWARM_TIMEOUT_MS", "2000L")
  }

  buildTypes {
    debug { buildConfigField("Boolean", "ENABLE_ANALYTICS", enableAnalyticsDebug.toString()) }
    release {
      isMinifyEnabled = true
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
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlinOptions { jvmTarget = "17" }
}

kotlin {
  jvmToolchain(21)
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
  implementation(project(":core-common"))
  implementation(project(":feature-exam"))
  implementation(project(":feature-auth"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.12.1")

  implementation("androidx.core:core-ktx:1.15.0")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
  implementation("androidx.activity:activity-compose:1.9.3")
  implementation("androidx.navigation:navigation-compose:2.8.3")
  implementation("androidx.compose.ui:ui:1.7.1")
  implementation("androidx.compose.ui:ui-tooling-preview:1.7.1")
  implementation("androidx.compose.foundation:foundation:1.7.1")
  implementation("androidx.compose.material3:material3:1.3.1")
  implementation("androidx.compose.material3:material3-window-size-class:1.3.1")
  implementation("androidx.compose.material:material-icons-extended:1.7.1")
  implementation("com.google.android.material:material:1.12.0")
  implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
  implementation("com.jakewharton.timber:timber:5.0.1")
  implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
  implementation("com.google.firebase:firebase-auth-ktx")
  implementation("com.google.firebase:firebase-analytics-ktx")
  implementation("com.google.firebase:firebase-crashlytics-ktx")
  implementation("com.google.dagger:hilt-android:2.52")
  ksp("com.google.dagger:hilt-compiler:2.52")

  debugImplementation("androidx.compose.ui:ui-tooling:1.7.1")
  debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.1")

  androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.7.1")
  androidTestImplementation("androidx.test.ext:junit:1.1.5")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

  testImplementation("junit:junit:4.13.2")
}

android {
testOptions { unitTests.isIncludeAndroidResources = true }
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
  allowUrl("https://spdx.org/licenses/MIT.txt") {
    because("MIT license used by org.codehaus.mojo:animal-sniffer-annotations:1.23")
  }
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

val verifyAssets by tasks.registering {
  group = LifecycleBasePlugin.VERIFICATION_GROUP
  description = "Verify presence of question bank and per-task bundles in module assets"

  doLast {
    val locales = listOf("en", "ru")
    val expectedTaskCount = 15
    val problems = mutableListOf<String>()

    fun exists(rel: String) = project.file(rel).exists()

    locales.forEach { locale ->
      val base = "src/main/assets/questions/$locale"

      if (!exists("$base/bank.v1.json")) {
        problems += "• Missing $base/bank.v1.json — copy via:  cp dist/questions/$locale/bank.v1.json $base/"
      }

      val tasksDir = project.file("$base/tasks")
      if (!tasksDir.isDirectory) {
        problems += "• Missing $base/tasks/ — copy via:  cp -R dist/questions/$locale/tasks $base/"
      } else {
        val count = tasksDir.listFiles { f -> f.isFile && f.extension == "json" }?.size ?: 0
        if (count != expectedTaskCount) {
          problems += "• Expected $expectedTaskCount task bundles under $base/tasks/ but found $count — rebuild & copy from dist."
        }
      }
    }

    if (!exists("src/main/assets/questions/index.json")) {
      problems += "• Missing src/main/assets/questions/index.json — copy via:  cp dist/questions/index.json src/main/assets/questions/"
    }

    if (problems.isNotEmpty()) {
      throw GradleException(
        buildString {
          appendLine("❌ verifyAssets failed — required question assets are missing:")
          problems.forEach { appendLine(it) }
          appendLine()
          appendLine("Run `bash scripts/build-questions-dist.sh` then copy `dist/questions/**` into `src/main/assets/questions/`.")
        }
      )
    } else {
      println("✅ Questions assets verified.")
    }
  }
}

plugins.withId("com.android.application") {
  val skip = (project.findProperty("skipVerifyAssets")?.toString()?.toBoolean() == true)
  if (!skip) {
    tasks.named("preBuild").configure { dependsOn(verifyAssets) }
  } else {
    logger.lifecycle("⚠️  skipVerifyAssets=true — verifyAssets is skipped for :app-android")
  }
}
