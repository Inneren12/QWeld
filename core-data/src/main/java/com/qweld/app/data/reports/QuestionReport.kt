package com.qweld.app.data.reports

import com.google.firebase.Timestamp

/**
 * Data model for question reports submitted to Firestore.
 * Reports allow users to flag questions with issues (wrong answers, typos, translations, etc.).
 * Stored in the "question_reports" collection with no PII.
 */
data class QuestionReport(
  // Core identifiers
  val questionId: String,
  val taskId: String,
  val blockId: String,
  val blueprintId: String,

  // Localization & mode
  val locale: String,
  val mode: String,

  // Reason
  val reasonCode: String,
  val reasonDetail: String? = null,
  val userComment: String? = null,

  // Position/context within attempt
  val questionIndex: Int? = null,
  val totalQuestions: Int? = null,
  val selectedChoiceIds: List<Int>? = null,
  val correctChoiceIds: List<Int>? = null,
  val blueprintTaskQuota: Int? = null,

  // Versions & environment
  val contentIndexSha: String? = null,
  val blueprintVersion: String? = null,
  val contentVersion: String? = null,
  val appVersionName: String? = null,
  val appVersionCode: Int? = null,
  val buildType: String? = null,
  val platform: String? = "android",
  val osVersion: String? = null,
  val deviceModel: String? = null,

  // Session/attempt context (no PII)
  val sessionId: String? = null,
  val attemptId: String? = null,
  val seed: Long? = null,
  val attemptKind: String? = null,

  // Admin / workflow
  val status: String = "OPEN",
  val createdAt: Timestamp? = null,
  val review: Map<String, Any?>? = null,
)
