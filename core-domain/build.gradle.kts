plugins { kotlin("jvm") }

kotlin { jvmToolchain(21) }

dependencies {
  implementation(kotlin("stdlib"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

  testImplementation(kotlin("test"))
  testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.3")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.3")
}

tasks.test { useJUnitPlatform() }
