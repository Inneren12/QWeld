package com.qweld.app

import android.app.Application
import android.util.Log
import java.util.Locale
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.qweld.app.data.logging.LogCollector
import com.qweld.app.data.logging.LogCollectorOwner
import com.qweld.app.env.AppEnvImpl
import com.qweld.app.error.AppErrorHandlerImpl
import com.qweld.app.error.CrashlyticsCrashReporter
import com.qweld.core.common.AppEnv
import timber.log.Timber

class QWeldApp : Application(), LogCollectorOwner {
  override val logCollector: LogCollector by lazy { LogCollector() }
  val appEnv: AppEnv by lazy { AppEnvImpl() }
  val appErrorHandler by lazy {
    AppErrorHandlerImpl(
      crashReporter = CrashlyticsCrashReporter(Firebase.crashlytics.takeIf { BuildConfig.ENABLE_ANALYTICS }),
      appEnv = appEnv,
      logCollector = logCollector,
      analyticsAllowedByBuild = BuildConfig.ENABLE_ANALYTICS,
      debugBehaviorEnabled = BuildConfig.DEBUG,
    )
  }

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
