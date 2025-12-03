package com.qweld.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.qweld.app.data.db.dao.AnswerDao
import com.qweld.app.data.db.dao.AttemptDao
import com.qweld.app.data.db.entities.AnswerEntity
import com.qweld.app.data.db.entities.AttemptEntity
import timber.log.Timber

internal const val QWELD_DB_VERSION = 2

private val MIGRATION_1_2 =
  object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL(
        "CREATE INDEX IF NOT EXISTS idx_attempts_started_at ON attempts(started_at DESC)",
      )
    }
  }

internal val QWELD_MIGRATIONS = arrayOf(MIGRATION_1_2)

@Database(
  entities = [AttemptEntity::class, AnswerEntity::class],
  version = QWELD_DB_VERSION,
  exportSchema = true,
)
abstract class QWeldDb : RoomDatabase() {
  abstract fun attemptDao(): AttemptDao

  abstract fun answerDao(): AnswerDao

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
