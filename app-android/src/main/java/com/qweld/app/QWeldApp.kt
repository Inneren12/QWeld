package com.qweld.app

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale
import com.qweld.app.data.logging.LogCollector
import com.qweld.app.data.logging.LogCollectorOwner
import timber.log.Timber
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import com.qweld.app.data.prefs.UserPrefsDataStore
import com.qweld.app.i18n.LocaleController

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
    val appLocales = AppCompatDelegate.getApplicationLocales()
    val localeTag = appLocales.toLanguageTags().ifBlank { Locale.getDefault().toLanguageTag() }
      // Применяем сохранённую локаль *до* первого UI, чтобы не было мигания
      runBlocking {
          runCatching {
              val prefs = UserPrefsDataStore(applicationContext)
              val tag = prefs.appLocaleFlow().first()
              LocaleController.apply(tag) // idempotent: ничего не делает, если уже такая же локаль
          }.onFailure { Timber.w(it, "[locale_init_skip]") }
      }
      Timber.i(
          "[app] start version=%s buildType=%s locale=%s | attrs=%s",
          BuildConfig.VERSION_NAME, BuildConfig.BUILD_TYPE, resources.configuration.locales, "{}"
      )
  }
}
