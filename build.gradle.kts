plugins {
  id("com.android.application") version "8.5.2" apply false
  id("com.android.library") version "8.5.2" apply false
  id("org.jetbrains.kotlin.android") version "2.0.20" apply false
  id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
  id("com.diffplug.spotless")
  id("io.gitlab.arturbosch.detekt") version "1.23.6"
  id("org.jetbrains.kotlinx.kover") version "0.7.6"
}

spotless {
  kotlin {
    target("**/*.kt")
    targetExclude("**/build/**")
    ktfmt().googleStyle()
  }
  kotlinGradle {
    target("**/*.gradle.kts")
    targetExclude("**/build/**")
    ktfmt().googleStyle()
  }
}

detekt {
  toolVersion = "1.23.6"
  buildUponDefaultConfig = true
  config.setFrom(files("$rootDir/detekt.yml"))
}

subprojects {
  plugins.withId("org.jetbrains.kotlin.android") {
    apply(plugin = "org.jetbrains.kotlinx.kover")
  }

  plugins.withId("org.jetbrains.kotlin.jvm") {
    apply(plugin = "org.jetbrains.kotlinx.kover")
  }
}

koverReport {
  defaults {
    xml {
      setReportFile(layout.buildDirectory.file("reports/kover/xml/report.xml"))
    }
  }
}
