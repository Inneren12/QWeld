package com.qweld.app.domain

sealed interface Outcome<out T> {
  data class Ok<T>(val value: T) : Outcome<T>

  sealed interface Err : Outcome<Nothing> {
    data class ContentNotFound(val path: String) : Err

    data class SchemaViolation(val path: String, val reason: String) : Err

    data class QuotaExceeded(val taskId: String, val required: Int, val have: Int) : Err

    data class IoFailure(val cause: Throwable) : Err
  }
}
