package com.qweld.app.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.qweld.app.data.db.dao.AnswerDao
import com.qweld.app.data.db.dao.AttemptDao
import com.qweld.app.data.db.entities.AnswerEntity
import com.qweld.app.data.db.entities.AttemptEntity

@Database(
  entities = [AttemptEntity::class, AnswerEntity::class],
  version = 1,
  exportSchema = false,
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
        .addCallback(loggingCallback())
        .build()
    }

    fun inMemory(context: Context): QWeldDb {
      return Room.inMemoryDatabaseBuilder(context, QWeldDb::class.java)
        .allowMainThreadQueries()
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
