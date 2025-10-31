package com.qweld.app

import android.app.Application
import android.util.Log
import java.util.Locale
import com.qweld.app.data.logging.LogCollector
import com.qweld.app.data.logging.LogCollectorOwner
import timber.log.Timber

class QWeldApp : Application(), LogCollectorOwner {
  override val logCollector: LogCollector by lazy { LogCollector() }

  override fun onCreate() {
    super.onCreate()
    Timber.plant(logCollector.asTree())
    if (BuildConfig.DEBUG) {
      Timber.plant(Timber.DebugTree())
    } else {
      Timber.plant(
        object : Timber.Tree() {
          override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority >= Log.INFO) {
              Log.println(priority, tag, message)
            }
          }
        }
      )
    }
    Timber.i(
      "[app] start version=%s buildType=%s locale=%s | attrs=%s",
      BuildConfig.VERSION_NAME,
      BuildConfig.BUILD_TYPE,
      Locale.getDefault(),
      "{}"
    )
  }
}
