package com.qweld.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB_NAME = "qweld-migration-test.db"

@RunWith(AndroidJUnit4::class)
class QWeldDbMigrationTest {

  private val instrumentation = InstrumentationRegistry.getInstrumentation()

  @get:Rule
  val helper = MigrationTestHelper(
    instrumentation,
    QWeldDb::class.java,
    emptyList(),
    FrameworkSQLiteOpenHelperFactory(),
  )

  @After
  fun tearDown() {
    ApplicationProvider.getApplicationContext<Context>().deleteDatabase(TEST_DB_NAME)
  }

  @Test
  fun migrateFromV1ToCurrent_preservesAttemptsAndAnswers() {
    ApplicationProvider.getApplicationContext<Context>().deleteDatabase(TEST_DB_NAME)

    helper.createDatabase(TEST_DB_NAME, 1).apply {
      execSQL(
        """
        INSERT INTO attempts(
          id, mode, locale, seed, question_count, started_at, finished_at,
          duration_sec, pass_threshold, score_pct
        ) VALUES(
          'attempt-1', 'exam', 'en', 42, 10, 1000, NULL, NULL, NULL, NULL
        )
        """
      )
      execSQL(
        """
        INSERT INTO answers(
          attempt_id, display_index, question_id, selected_id, correct_id,
          is_correct, time_spent_sec, seen_at, answered_at
        ) VALUES(
          'attempt-1', 0, 'question-1', 'answer-1', 'answer-1', 1, 12, 1001, 1002
        )
        """
      )
      execSQL(
        """
        INSERT INTO answers(
          attempt_id, display_index, question_id, selected_id, correct_id,
          is_correct, time_spent_sec, seen_at, answered_at
        ) VALUES(
          'attempt-1', 1, 'question-2', 'answer-2', 'answer-3', 0, 15, 1010, 1020
        )
        """
      )
      close()
    }

    helper.runMigrationsAndValidate(TEST_DB_NAME, QWELD_DB_VERSION, true, *QWELD_MIGRATIONS).close()

    val context = ApplicationProvider.getApplicationContext<Context>()
    val roomDb = Room.databaseBuilder(context, QWeldDb::class.java, TEST_DB_NAME)
      .allowMainThreadQueries()
      .build()

    runBlocking {
      val attempt = roomDb.attemptDao().getById("attempt-1")
      assertNotNull(attempt)
      assertEquals("exam", attempt!!.mode)
      assertEquals(10, attempt.questionCount)
      assertEquals("en", attempt.locale)

      val answers = roomDb.answerDao().listByAttempt("attempt-1")
      assertEquals(2, answers.size)
      assertEquals("question-1", answers.first().questionId)

      val wrongQuestions = roomDb.answerDao().listWrongByAttempt("attempt-1")
      assertEquals(listOf("question-2"), wrongQuestions)

      val aggregate = roomDb.answerDao().countByQuestion("question-1")
      assertNotNull(aggregate)
      assertEquals(1, aggregate!!.attempts)
      assertEquals(1, aggregate.correct)
      assertEquals(true, aggregate.lastIsCorrect)

      roomDb.query(SimpleSQLiteQuery("PRAGMA index_list('attempts')")).use { cursor ->
        val nameIndex = cursor.getColumnIndexOrThrow("name")
        var hasStartedAtIndex = false
        while (cursor.moveToNext()) {
          if (cursor.getString(nameIndex) == "idx_attempts_started_at") {
            hasStartedAtIndex = true
            break
          }
        }
        assertTrue(hasStartedAtIndex)
      }
    }

    roomDb.close()
  }

  @Test
  fun migrateFromV5ToCurrent_preservesAttemptsAnswersAndTimer() {
    ApplicationProvider.getApplicationContext<Context>().deleteDatabase(TEST_DB_NAME)

    helper.createDatabase(TEST_DB_NAME, 5).apply {
      execSQL(
        """
        INSERT INTO attempts(
          id, mode, locale, seed, question_count, started_at, finished_at,
          duration_sec, pass_threshold, score_pct, remaining_time_ms
        ) VALUES(
          'attempt-upgrade-1', 'exam', 'en', 7, 3, 5000, NULL, NULL, NULL, NULL, 123456
        )
        """,
      )

      execSQL(
        """
        INSERT INTO answers(
          attempt_id, display_index, question_id, selected_id, correct_id,
          is_correct, time_spent_sec, seen_at, answered_at
        ) VALUES(
          'attempt-upgrade-1', 0, 'question-100', 'choice-200', 'choice-200', 1, 8, 5001, 5002
        )
        """,
      )

      execSQL(
        """
        INSERT INTO answers(
          attempt_id, display_index, question_id, selected_id, correct_id,
          is_correct, time_spent_sec, seen_at, answered_at
        ) VALUES(
          'attempt-upgrade-1', 1, 'question-101', 'choice-201', 'choice-202', 0, 12, 5010, 5015
        )
        """,
      )

      execSQL(
        """
        INSERT INTO answers(
          attempt_id, display_index, question_id, selected_id, correct_id,
          is_correct, time_spent_sec, seen_at, answered_at
        ) VALUES(
          'attempt-upgrade-1', 2, 'question-102', 'choice-203', 'choice-203', 1, 10, 5020, 5030
        )
        """,
      )

      close()
    }

    helper.runMigrationsAndValidate(TEST_DB_NAME, QWELD_DB_VERSION, true, *QWELD_MIGRATIONS).close()

    val context = ApplicationProvider.getApplicationContext<Context>()
    val roomDb = Room.databaseBuilder(context, QWeldDb::class.java, TEST_DB_NAME)
      .allowMainThreadQueries()
      .build()

    runBlocking {
      val attempt = roomDb.attemptDao().getById("attempt-upgrade-1")
      assertNotNull(attempt)
      assertEquals("exam", attempt!!.mode)
      assertEquals(3, attempt.questionCount)
      assertEquals("en", attempt.locale)
      assertEquals(123456L, attempt.remainingTimeMs)

      val answers = roomDb.answerDao().listByAttempt("attempt-upgrade-1")
      assertEquals(3, answers.size)
      assertEquals(
        setOf("question-100", "question-101", "question-102"),
        answers.map { it.questionId }.toSet(),
      )

      val answerByQuestion = answers.associateBy { it.questionId }
      assertEquals("choice-200", answerByQuestion.getValue("question-100").selectedId)
      assertEquals("choice-201", answerByQuestion.getValue("question-101").selectedId)
      assertEquals("choice-203", answerByQuestion.getValue("question-102").selectedId)
    }

    roomDb.close()
  }
}
