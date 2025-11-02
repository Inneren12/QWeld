package com.qweld.app.feature.exam.data

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Test

class BlueprintJsonLoaderTest {
  private val loader = BlueprintJsonLoader()

  @Test
  fun quotaZeroThrows() {
    val error = assertFailsWith<IllegalStateException> { loader.decode(blueprintJson(quota = 0)) }
    assertEquals("Each task quota must be positive: [A-2]", error.message)
  }

  @Test
  fun quotaNegativeThrows() {
    val error = assertFailsWith<IllegalStateException> { loader.decode(blueprintJson(quota = -1)) }
    assertEquals("Each task quota must be positive: [A-2]", error.message)
  }

  private fun blueprintJson(quota: Int): String {
    val total = 1 + quota + 2
    return """
      {
        \"questionCount\": $total,
        \"blocks\": [
          {
            \"id\": \"A\",
            \"tasks\": [
              { \"id\": \"A-1\", \"quota\": 1 },
              { \"id\": \"A-2\", \"quota\": $quota },
              { \"id\": \"A-3\", \"quota\": 2 }
            ]
          }
        ]
      }
    """.trimIndent()
  }
}
