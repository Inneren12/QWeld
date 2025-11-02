package com.qweld.core.common.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogxTest {
  private val buildMethod = Logx::class.java.getDeclaredMethod(
    "build",
    String::class.java,
    String::class.java,
    arrayOf<Pair<String, Any?>>()::class.java,
  ).apply { isAccessible = true }

  @Test
  fun `build matches expected format`() {
    val message = invokeBuild("[load]", "start", arrayOf("src" to "bank", "ok" to true))
    val regex = Regex("^\\[(\\w+)]\\s+\\S+(?:\\s+\\w+=\\S+)*$")
    assertTrue(regex.matches(message), "message=\"$message\" does not match format")
  }

  @Test
  fun `key value pairs preserve order`() {
    val message = invokeBuild(
      "[prewarm]",
      "step",
      arrayOf("first" to 1, "second" to 2, "third" to 3),
    )
    assertEquals("[prewarm] step first=1 second=2 third=3", message)
  }

  private fun invokeBuild(
    prefix: String,
    event: String,
    kv: Array<Pair<String, Any?>>, // ensure Pair retains order
  ): String = buildMethod.invoke(Logx, prefix, event, kv) as String
}
