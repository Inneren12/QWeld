plugins {
    id("com.android.application") version "8.5.2" apply false
    id("com.android.library") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    id("com.diffplug.spotless")
    id("io.gitlab.arturbosch.detekt") version "1.23.6"
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
