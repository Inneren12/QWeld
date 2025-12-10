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

internal const val QWELD_DB_VERSION = 3

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
        "CREATE INDEX IF NOT EXISTS idx_queued_question_reports_created_at ON queued_question_reports(created_at ASC)",
      )
      db.execSQL(
        "CREATE INDEX IF NOT EXISTS idx_queued_question_reports_question_id ON queued_question_reports(question_id)",
      )
    }
  }

internal val QWELD_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

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
