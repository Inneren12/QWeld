package com.qweld.app.feature.exam.data

import com.qweld.app.data.content.questions.IntegrityMismatchException
import java.io.FileNotFoundException

/**
 * Typed error model for content loading failures.
 *
 * This sealed hierarchy replaces generic string-based error reporting with structured,
 * actionable error types that can be:
 * 1. Logged with appropriate context
 * 2. Mapped to specific UI states
 * 3. Tested comprehensively
 * 4. Analyzed for diagnostics
 *
 * ## Error Categories:
 * - **Manifest Errors**: Missing or invalid index.json
 * - **Task Errors**: Per-task file loading failures
 * - **Integrity Errors**: Hash mismatch indicating corruption
 * - **Parse Errors**: Invalid JSON or schema violations
 * - **Locale Errors**: Unsupported or unavailable locale
 * - **Unknown Errors**: Unexpected failures with cause
 *
 * ## Usage:
 * ```kotlin
 * when (error) {
 *   is ContentLoadError.MissingManifest -> log "Missing manifest at ${error.path}"
 *   is ContentLoadError.MissingTaskFile -> log "Task ${error.taskId} not found"
 *   is ContentLoadError.IntegrityMismatch -> log "Corruption detected in ${error.path}"
 *   // ... handle other cases
 * }
 * ```
 */
sealed class ContentLoadError {

  /**
   * Abstract diagnostic message for logging and debugging.
   * Includes contextual information without exposing sensitive data.
   */
  abstract val diagnosticMessage: String

  /**
   * The underlying cause, if applicable.
   * Null for errors that don't wrap exceptions (e.g., missing files).
   */
  open val cause: Throwable? = null

  // ============================================================================
  // Manifest-related errors
  // ============================================================================

  /**
   * The locale-specific index.json manifest file is missing.
   *
   * **Impact**: Cannot verify asset integrity for this locale.
   * **Recovery**: Try different locale or check asset packaging.
   *
   * @param locale The locale for which the manifest was not found (e.g., "en", "ru")
   * @param path The expected manifest path (e.g., "questions/en/index.json")
   */
  data class MissingManifest(
    val locale: String,
    val path: String,
  ) : ContentLoadError() {
    override val diagnosticMessage: String
      get() = "Manifest missing: locale=$locale path=$path"
  }

  /**
   * The manifest file exists but contains invalid JSON or schema violations.
   *
   * **Impact**: Cannot parse asset metadata for integrity checks.
   * **Recovery**: Re-package assets or check schema compliance.
   *
   * @param locale The locale for which the manifest is invalid
   * @param path The manifest path
   * @param reason Human-readable description of the parse error
   * @param cause The underlying parsing exception
   */
  data class InvalidManifest(
    val locale: String,
    val path: String,
    val reason: String,
    override val cause: Throwable,
  ) : ContentLoadError() {
    override val diagnosticMessage: String
      get() = "Manifest invalid: locale=$locale path=$path reason=$reason"
  }

  // ============================================================================
  // Task-specific file errors
  // ============================================================================

  /**
   * A specific task file could not be found.
   *
   * **Impact**: Cannot load questions for this task.
   * **Recovery**: Fallback to bank loading or different locale.
   *
   * @param taskId The task identifier (e.g., "A-1", "B-2")
   * @param locale The locale being loaded
   * @param path The expected task file path (e.g., "questions/en/tasks/A-1.json")
   */
  data class MissingTaskFile(
    val taskId: String,
    val locale: String,
    val path: String,
  ) : ContentLoadError() {
    override val diagnosticMessage: String
      get() = "Task file missing: taskId=$taskId locale=$locale path=$path"
  }

  /**
   * A task file exists but could not be read or parsed.
   *
   * **Impact**: Cannot load questions for this task.
   * **Recovery**: Fallback to bank loading or different locale.
   *
   * @param taskId The task identifier
   * @param locale The locale being loaded
   * @param path The task file path
   * @param reason Human-readable error description
   * @param cause The underlying exception (parse error, I/O error, etc.)
   */
  data class TaskFileReadError(
    val taskId: String,
    val locale: String,
    val path: String,
    val reason: String,
    override val cause: Throwable,
  ) : ContentLoadError() {
    override val diagnosticMessage: String
      get() = "Task file read error: taskId=$taskId locale=$locale path=$path reason=$reason"
  }

  // ============================================================================
  // Integrity verification errors
  // ============================================================================

  /**
   * An asset file's SHA-256 hash does not match the expected value from the manifest.
   *
   * **Impact**: File may be corrupted, truncated, or tampered with.
   * **Recovery**: Re-download app or reinstall.
   *
   * @param locale The locale being loaded
   * @param path The path to the corrupted file
   * @param expectedHash The SHA-256 hash from the manifest (or null if missing)
   * @param actualHash The computed SHA-256 hash (or null if file unreadable)
   */
  data class IntegrityMismatch(
    val locale: String,
    val path: String,
    val expectedHash: String?,
    val actualHash: String?,
  ) : ContentLoadError() {
    override val diagnosticMessage: String
      get() = "Integrity mismatch: locale=$locale path=$path expected=$expectedHash actual=$actualHash"

    companion object {
      /**
       * Creates an IntegrityMismatch from AssetIntegrityGuard's exception.
       */
      fun fromException(locale: String, exception: IntegrityMismatchException): IntegrityMismatch {
        return IntegrityMismatch(
          locale = locale,
          path = exception.path,
          expectedHash = exception.expected,
          actualHash = exception.actual,
        )
      }
    }
  }

  // ============================================================================
  // JSON parsing and schema errors
  // ============================================================================

  /**
   * A file contains invalid JSON or does not conform to the expected schema.
   *
   * **Impact**: Cannot deserialize questions or metadata.
   * **Recovery**: Re-package assets or fix schema compliance.
   *
   * @param locale The locale being loaded
   * @param path The path to the invalid file
   * @param reason Human-readable description of the schema violation
   * @param cause The underlying serialization exception
   */
  data class InvalidJson(
    val locale: String,
    val path: String,
    val reason: String,
    override val cause: Throwable,
  ) : ContentLoadError() {
    override val diagnosticMessage: String
      get() = "Invalid JSON: locale=$locale path=$path reason=$reason"
  }

  // ============================================================================
  // Locale availability errors
  // ============================================================================

  /**
   * The requested locale has no content available.
   *
   * **Impact**: Cannot load questions for this locale.
   * **Recovery**: Fallback to English or show locale selector.
   *
   * @param requestedLocale The locale that was requested (e.g., "fr", "es")
   * @param availableLocales List of locales that have content (e.g., ["en", "ru"])
   */
  data class UnsupportedLocale(
    val requestedLocale: String,
    val availableLocales: List<String>,
  ) : ContentLoadError() {
    override val diagnosticMessage: String
      get() = "Unsupported locale: requested=$requestedLocale available=${availableLocales.joinToString(",")}"
  }

  // ============================================================================
  // Fallback and generic errors
  // ============================================================================

  /**
   * A bank file (questions/{locale}/bank.v1.json) could not be found.
   *
   * **Impact**: Fallback loading strategy fails.
   * **Recovery**: Check asset packaging or try different locale.
   *
   * @param locale The locale for which the bank is missing
   * @param path The expected bank file path
   */
  data class MissingBank(
    val locale: String,
    val path: String,
  ) : ContentLoadError() {
    override val diagnosticMessage: String
      get() = "Bank missing: locale=$locale path=$path"
  }

  /**
   * A bank file exists but is corrupted or invalid.
   *
   * **Impact**: Fallback loading strategy fails.
   * **Recovery**: Re-download app or reinstall.
   *
   * @param locale The locale for which the bank is invalid
   * @param path The bank file path
   * @param reason Human-readable error description
   * @param cause The underlying exception
   */
  data class BankFileError(
    val locale: String,
    val path: String,
    val reason: String,
    override val cause: Throwable,
  ) : ContentLoadError() {
    override val diagnosticMessage: String
      get() = "Bank file error: locale=$locale path=$path reason=$reason"
  }

  /**
   * An unexpected error occurred that doesn't fit other categories.
   *
   * **Impact**: Cannot load questions; exact failure mode unclear.
   * **Recovery**: Check logs and underlying cause.
   *
   * @param context Brief description of what was being attempted (e.g., "loading_per_task")
   * @param locale The locale being loaded (if applicable)
   * @param cause The underlying exception
   */
  data class Unknown(
    val context: String,
    val locale: String?,
    override val cause: Throwable,
  ) : ContentLoadError() {
    override val diagnosticMessage: String
      get() = "Unknown error: context=$context locale=${locale ?: "none"} cause=${cause.javaClass.simpleName}: ${cause.message?.take(100)}"
  }

  // ============================================================================
  // Utility functions
  // ============================================================================

  companion object {
    /**
     * Converts a generic Throwable to an appropriate ContentLoadError.
     * Used for catching unexpected exceptions and wrapping them in the error model.
     */
    fun fromThrowable(
      throwable: Throwable,
      context: String,
      locale: String? = null,
      path: String? = null,
    ): ContentLoadError {
      return when (throwable) {
        is FileNotFoundException -> {
          if (path != null && locale != null) {
            when {
              path.contains("/index.json") -> MissingManifest(locale, path)
              path.contains("/bank.") -> MissingBank(locale, path)
              path.contains("/tasks/") -> {
                val taskId = path.substringAfterLast("/").substringBefore(".json")
                MissingTaskFile(taskId, locale, path)
              }
              else -> Unknown(context, locale, throwable)
            }
          } else {
            Unknown(context, locale, throwable)
          }
        }
        is IntegrityMismatchException -> {
          IntegrityMismatch.fromException(locale ?: "unknown", throwable)
        }
        is kotlinx.serialization.SerializationException -> {
          if (path != null && locale != null) {
            val reason = throwable.message?.take(200) ?: "Serialization failed"
            InvalidJson(locale, path, reason, throwable)
          } else {
            Unknown(context, locale, throwable)
          }
        }
        else -> Unknown(context, locale, throwable)
      }
    }
  }
}
