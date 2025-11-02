package com.qweld.app.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import com.qweld.app.data.db.dao.AnswerDao
import com.qweld.app.data.db.dao.AttemptDao
import com.qweld.app.data.db.entities.AnswerEntity
import com.qweld.app.data.db.entities.AttemptEntity

internal const val QWELD_DB_VERSION = 2

private val MIGRATION_1_2 =
  object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
      database.execSQL(
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

    private fun loggingCallback(): RoomDatabase.Callback {
      return object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
          super.onOpen(db)
          Log.i(TAG, "[db_open] version=${db.version}")
        }
      }
    }
  }
}
