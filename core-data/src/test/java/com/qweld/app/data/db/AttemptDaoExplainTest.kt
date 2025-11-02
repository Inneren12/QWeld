package com.qweld.app.data.db

import android.content.Context
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class AttemptDaoExplainTest {
  private lateinit var context: Context
  private lateinit var db: QWeldDb

  @BeforeTest
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    db = QWeldDb.inMemory(context)
  }

  @AfterTest
  fun tearDown() {
    db.close()
  }

  @Test
  fun listRecent_usesStartedAtIndex() {
    val explainQuery =
      SimpleSQLiteQuery(
        "EXPLAIN QUERY PLAN SELECT * FROM attempts ORDER BY started_at DESC LIMIT 5",
      )

    db.query(explainQuery).use { cursor ->
      val detailIndex = cursor.getColumnIndexOrThrow("detail")
      val details = buildList {
        while (cursor.moveToNext()) {
          add(cursor.getString(detailIndex))
        }
      }

      assertTrue(
        details.any { it.contains("USING INDEX idx_attempts_started_at") },
        "Expected EXPLAIN to mention USING INDEX idx_attempts_started_at but was: $details",
      )
    }
  }
}
