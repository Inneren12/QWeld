package com.qweld.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.qweld.app.data.db.dao.AnswerDao
import com.qweld.app.data.db.dao.AttemptDao
import com.qweld.app.data.db.dao.QueuedQuestionReportDao
import com.qweld.app.data.db.entities.AnswerEntity
import com.qweld.app.data.db.entities.AttemptEntity
import com.qweld.app.data.db.entities.QueuedQuestionReportEntity
import timber.log.Timber

const val QWELD_DB_VERSION = 4

private val MIGRATION_1_2 =
  object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL(
        "CREATE INDEX IF NOT EXISTS idx_attempts_started_at ON attempts(started_at DESC)",
      )
    }
  }

private val MIGRATION_2_3 =
  object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS queued_question_reports (
          id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          question_id TEXT NOT NULL,
          locale TEXT NOT NULL,
          reason_code TEXT NOT NULL,
          payload TEXT NOT NULL,
          attempt_count INTEGER NOT NULL,
          last_attempt_at INTEGER,
          created_at INTEGER NOT NULL
        )
        """.trimIndent()
      )

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_queued_question_reports_created_at ON queued_question_reports(created_at ASC)",
            )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_queued_question_reports_question_id ON queued_question_reports(question_id)",
            )
    }
  }

/**
 * Migration to align the on-disk schema with the current entities for attempts/answers.
 *
 * We recreate both tables with the expected columns and copy the data over.
 */
private val MIGRATION_3_4 =
  object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
      // --- attempts ---
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS attempts_new (
          id TEXT NOT NULL PRIMARY KEY,
          mode TEXT NOT NULL,
          locale TEXT NOT NULL,
          seed INTEGER NOT NULL,
          question_count INTEGER NOT NULL,
          started_at INTEGER NOT NULL,
          finished_at INTEGER,
          duration_sec INTEGER,
          pass_threshold INTEGER,
          score_pct REAL
        )
        """.trimIndent(),
      )

      db.execSQL(
        """
        INSERT INTO attempts_new(
          id, mode, locale, seed, question_count, started_at,
          finished_at, duration_sec, pass_threshold, score_pct
        )
        SELECT
          id, mode, locale, seed, question_count, started_at,
          finished_at, duration_sec, pass_threshold, score_pct
        FROM attempts
        """.trimIndent(),
      )

      db.execSQL("DROP TABLE attempts")
      db.execSQL("ALTER TABLE attempts_new RENAME TO attempts")

      // Recreate index from MIGRATION_1_2 on the new table
      db.execSQL(
        "CREATE INDEX IF NOT EXISTS idx_attempts_started_at ON attempts(started_at DESC)",
      )

        // --- answers ---
        db.execSQL(
            """
        CREATE TABLE IF NOT EXISTS answers_new (
          attempt_id TEXT NOT NULL,
          display_index INTEGER NOT NULL,
          question_id TEXT NOT NULL,
          selected_id TEXT NOT NULL,
          correct_id TEXT NOT NULL,
          is_correct INTEGER NOT NULL,
          time_spent_sec INTEGER NOT NULL,
          seen_at INTEGER NOT NULL,
          answered_at INTEGER NOT NULL,
          PRIMARY KEY(attempt_id, display_index),
          FOREIGN KEY(attempt_id) REFERENCES attempts(id) ON DELETE CASCADE
        )
        """.trimIndent(),
            )

      db.execSQL(
        """
        INSERT INTO answers_new(
          attempt_id, display_index, question_id,
          selected_id, correct_id, is_correct,
          time_spent_sec, seen_at, answered_at
        )
        SELECT
          attempt_id, display_index, question_id,
          selected_id, correct_id, is_correct,
          time_spent_sec, seen_at, answered_at
        FROM answers
        """.trimIndent(),
      )

      db.execSQL("DROP TABLE answers")
      db.execSQL("ALTER TABLE answers_new RENAME TO answers")

        // Индексы как в текущей схеме Room
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_answers_question_id ON answers(question_id)",
            )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_answers_attempt_id_display_index ON answers(attempt_id, display_index)",
            )
    }
  }

internal val QWELD_MIGRATIONS = arrayOf(
  MIGRATION_1_2,
  MIGRATION_2_3,
  MIGRATION_3_4,
)

@Database(
  entities = [AttemptEntity::class, AnswerEntity::class, QueuedQuestionReportEntity::class],
  version = QWELD_DB_VERSION,
  exportSchema = true,
)
abstract class QWeldDb : RoomDatabase() {
  abstract fun attemptDao(): AttemptDao

  abstract fun answerDao(): AnswerDao

  abstract fun queuedQuestionReportDao(): QueuedQuestionReportDao

  companion object {
    private const val TAG = "QWeldDb"
    private const val DB_NAME = "qweld.db"

    fun create(
      context: Context,
      name: String = DB_NAME,
    ): QWeldDb {
      return Room.databaseBuilder(context, QWeldDb::class.java, name)
        .addMigrations(*QWELD_MIGRATIONS)
        .addCallback(loggingCallback())
        .build()
    }

    fun inMemory(context: Context): QWeldDb {
      return Room.inMemoryDatabaseBuilder(context, QWeldDb::class.java)
        .allowMainThreadQueries()
        .addMigrations(*QWELD_MIGRATIONS)
        .addCallback(loggingCallback())
        .build()
    }

    private fun loggingCallback(): Callback {
      return object : Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
          super.onOpen(db)
            Timber.tag(TAG).i("[db_open] version=%d", db.version)
        }
      }
    }
  }
}
