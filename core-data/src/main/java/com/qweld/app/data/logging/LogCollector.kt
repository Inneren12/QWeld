package com.qweld.app.data.logging

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import java.io.OutputStreamWriter
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

class LogCollector(
  private val capacity: Int = DEFAULT_CAPACITY,
  private val clock: Clock = Clock.systemUTC(),
  private val json: Json = DEFAULT_JSON,
) {
  private val lock = ReentrantLock()
  private val entries = ArrayDeque<LogEntry>(capacity)

  fun asTree(): Timber.Tree {
    return object : Timber.Tree() {
      override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        record(priority, tag, message, t)
      }
    }
  }

  fun record(priority: Int, tag: String?, message: String, throwable: Throwable?) {
    val entry = LogEntry(
      timestamp = clock.instant(),
      priority = priority,
      tag = normalizeTag(tag),
      message = normalizeMessage(message),
      throwable = throwable?.let(::sanitizeThrowable),
    )
    lock.withLock {
      if (entries.size == capacity) {
        entries.removeFirst()
      }
      entries.addLast(entry)
    }
  }

  fun export(format: LogExportFormat): LogExportResult {
    val snapshot = lock.withLock { entries.toList() }
    val exportedAt = clock.instant()
    val content = when (format) {
      LogExportFormat.TEXT -> snapshot.joinToString(separator = "\n") { it.renderText() }
      LogExportFormat.JSON -> json.encodeToString(
        LogExportPayload(
          schema = LOG_EXPORT_SCHEMA,
          exportedAt = ISO_FORMATTER.format(exportedAt),
          entries = snapshot.map { it.toJsonEntry() },
        ),
      )
    }
    return LogExportResult(
      content = content,
      entryCount = snapshot.size,
      exportedAt = exportedAt,
      format = format,
    )
  }

  fun createDocumentName(format: LogExportFormat): String {
    val timestamp = FILE_NAME_FORMATTER.format(clock.instant())
    return "QWeld_Logs_${timestamp}.${format.extension}"
  }

  fun entryCount(): Int = lock.withLock { entries.size }

  private fun normalizeTag(tag: String?): String {
    return tag?.takeIf { it.isNotBlank() } ?: DEFAULT_TAG
  }

  private fun normalizeMessage(message: String): String {
    val trimmed = message.trimEnd()
    return if (trimmed.contains("| attrs=")) trimmed else "$trimmed | attrs={}"
  }

  private fun sanitizeThrowable(throwable: Throwable): ThrowableSummary {
    val reason = throwable.message?.takeIf { it.isNotBlank() } ?: throwable::class.java.simpleName
    return ThrowableSummary(type = throwable::class.java.simpleName, message = reason)
  }

  private fun LogEntry.renderText(): String {
    val errorSuffix = throwable?.let { " | error=${it.type}:${it.message}" } ?: ""
    return "[${ISO_FORMATTER.format(timestamp)}] [${tag}] ${message}$errorSuffix"
  }

  private fun LogEntry.toJsonEntry(): LogJsonEntry {
    return LogJsonEntry(
      timestamp = ISO_FORMATTER.format(timestamp),
      priority = priorityLabel(priority),
      tag = tag,
      message = message,
      error = throwable,
    )
  }

  private fun priorityLabel(priority: Int): String {
    return when (priority) {
      android.util.Log.VERBOSE -> "VERBOSE"
      android.util.Log.DEBUG -> "DEBUG"
      android.util.Log.INFO -> "INFO"
      android.util.Log.WARN -> "WARN"
      android.util.Log.ERROR -> "ERROR"
      android.util.Log.ASSERT -> "ASSERT"
      else -> "UNKNOWN"
    }
  }

  data class LogExportResult(
    val content: String,
    val entryCount: Int,
    val exportedAt: Instant,
    val format: LogExportFormat,
  ) {
    val exportedAtIso: String get() = ISO_FORMATTER.format(exportedAt)
  }

  private data class LogEntry(
    val timestamp: Instant,
    val priority: Int,
    val tag: String,
    val message: String,
    val throwable: ThrowableSummary?,
  )

  @Serializable
  private data class LogExportPayload(
    val schema: String,
    val exportedAt: String,
    val entries: List<LogJsonEntry>,
  )

  @Serializable
  private data class LogJsonEntry(
    val timestamp: String,
    val priority: String,
    val tag: String,
    val message: String,
    val error: ThrowableSummary? = null,
  )

  @Serializable
  data class ThrowableSummary(
    val type: String,
    val message: String,
  )

  companion object {
    private const val DEFAULT_CAPACITY = 512
    private const val DEFAULT_TAG = "QWeld"
    private const val LOG_EXPORT_SCHEMA = "qweld.logs.v1"
    private val ISO_FORMATTER: DateTimeFormatter =
      DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC)
    private val FILE_NAME_FORMATTER: DateTimeFormatter =
      DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneOffset.UTC)
    private val DEFAULT_JSON = Json { prettyPrint = true }
  }
}

enum class LogExportFormat(val mimeType: String, val extension: String) {
  TEXT("text/plain", "txt"),
  JSON("application/json", "json"),
  ;

  val label: String get() = name.lowercase(Locale.US)
}

interface LogCollectorOwner {
  val logCollector: LogCollector
}

fun Context.findLogCollector(): LogCollector? {
  val application = applicationContext
  return if (application is LogCollectorOwner) application.logCollector else null
}

suspend fun LogCollector.writeTo(
  context: Context,
  uri: Uri,
  format: LogExportFormat,
): LogCollector.LogExportResult {
  val result = export(format)
  val resolver = context.contentResolver
  writeContent(resolver, uri, result.content)
  return result
}

private suspend fun writeContent(
  resolver: ContentResolver,
  uri: Uri,
  content: String,
) {
  withContext(Dispatchers.IO) {
    val stream = resolver.openOutputStream(uri)
      ?: error("Unable to open output stream for $uri")
    stream.use { output ->
      OutputStreamWriter(output, Charsets.UTF_8).use { writer -> writer.write(content) }
    }
  }
}
