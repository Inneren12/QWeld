package com.qweld.core.common.logging

import android.annotation.SuppressLint
import timber.log.Timber

enum class LogTag(val raw: String) {
  BLUEPRINT_LOAD("blueprint_load"),
  PREWARM("prewarm"),
  LOAD("load"),
  INTEGRITY("integrity"),
  DEFICIT("deficit"),
}

object Logx {
  fun d(tag: LogTag, event: String, vararg kv: Pair<String, Any?>) =
    log(Level.DEBUG, tag, event, null, kv)

  fun d(tag: LogTag, event: String, throwable: Throwable, vararg kv: Pair<String, Any?>) =
    log(Level.DEBUG, tag, event, throwable, kv)

  fun i(tag: LogTag, event: String, vararg kv: Pair<String, Any?>) =
    log(Level.INFO, tag, event, null, kv)

  fun i(tag: LogTag, event: String, throwable: Throwable, vararg kv: Pair<String, Any?>) =
    log(Level.INFO, tag, event, throwable, kv)

  fun w(tag: LogTag, event: String, vararg kv: Pair<String, Any?>) =
    log(Level.WARN, tag, event, null, kv)

  fun w(tag: LogTag, event: String, throwable: Throwable, vararg kv: Pair<String, Any?>) =
    log(Level.WARN, tag, event, throwable, kv)

  fun e(tag: LogTag, event: String, vararg kv: Pair<String, Any?>) =
    log(Level.ERROR, tag, event, null, kv)

  fun e(tag: LogTag, event: String, throwable: Throwable, vararg kv: Pair<String, Any?>) =
    log(Level.ERROR, tag, event, throwable, kv)

  @SuppressLint("TimberExceptionLogging")
  private fun log(
    level: Level,
    tag: LogTag,
    event: String,
    throwable: Throwable?,
    kv: Array<out Pair<String, Any?>>,
  ) {
    val message = build(prefix = "[${tag.raw}]", event = event, kv = kv)
    when {
      throwable != null -> when (level) {
        Level.DEBUG -> Timber.d(throwable, message)
        Level.INFO -> Timber.i(throwable, message)
        Level.WARN -> Timber.w(throwable, message)
        Level.ERROR -> Timber.e(throwable, message)
      }
      else -> when (level) {
        Level.DEBUG -> Timber.d(message)
        Level.INFO -> Timber.i(message)
        Level.WARN -> Timber.w(message)
        Level.ERROR -> Timber.e(message)
      }
    }
  }

  private fun build(
    prefix: String,
    event: String,
    kv: Array<out Pair<String, Any?>>,
  ): String =
    buildString {
      append(prefix)
      append(' ')
      append(event)
      if (kv.isNotEmpty()) {
        kv.forEach { (key, value) ->
          append(' ')
          append(key)
          append('=')
          append(value)
        }
      }
    }

  private enum class Level { DEBUG, INFO, WARN, ERROR }
}
