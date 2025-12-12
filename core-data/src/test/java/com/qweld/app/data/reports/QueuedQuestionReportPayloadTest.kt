package com.qweld.app.data.reports

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class QueuedQuestionReportPayloadTest {
  private val json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
  }

  @Test
  fun `decode accepts legacy osVersion and maps to androidVersion`() {
    val payload = json.decodeFromString<QueuedQuestionReportPayload>(
      minimalPayloadJson(osVersion = "33")
    )

    assertEquals("33", payload.legacyOsVersion)
    val report = payload.toQuestionReport()
    assertEquals("33", report.androidVersion)
  }

  @Test
  fun `decode prefers androidVersion when both are present`() {
    val payload = json.decodeFromString<QueuedQuestionReportPayload>(
      minimalPayloadJson(osVersion = "32", androidVersion = "34")
    )

    val report = payload.toQuestionReport()
    assertEquals("34", report.androidVersion)
  }

  @Test
  fun `decode ignores unknown fields in queued payload`() {
    val rawJson = minimalPayloadJson(androidVersion = "35", extraField = "\"unexpected\":\"keepme\"")

    val payload = json.decodeFromString<QueuedQuestionReportPayload>(rawJson)

    assertNotNull(payload)
    assertEquals("35", payload.toQuestionReport().androidVersion)
  }

  private fun minimalPayloadJson(
    osVersion: String? = null,
    androidVersion: String? = null,
    extraField: String? = null,
  ): String {
    val fields = buildList {
      add("\"questionId\":\"Q-1\"")
      add("\"taskId\":\"T-1\"")
      add("\"blockId\":\"B-1\"")
      add("\"blueprintId\":\"BP-1\"")
      add("\"locale\":\"en\"")
      add("\"mode\":\"practice\"")
      add("\"reasonCode\":\"typo\"")
      osVersion?.let { add("\"osVersion\":\"$it\"") }
      androidVersion?.let { add("\"androidVersion\":\"$it\"") }
      extraField?.let { add(it) }
    }

    return "{${fields.joinToString(separator = ",")}}"
  }
}
