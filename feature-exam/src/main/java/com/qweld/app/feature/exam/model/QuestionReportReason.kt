package com.qweld.app.feature.exam.model

/**
 * Enum representing the reason codes for reporting a question.
 * These will be stored in Firestore as part of QuestionReport.
 */
enum class QuestionReportReason(val code: String) {
  WRONG_ANSWER("wrong_answer"),
  OUTDATED_CONTENT("outdated_content"),
  BAD_WORDING("bad_wording"),
  TYPO("typo"),
  TRANSLATION_ISSUE("translation_issue"),
  UI_ISSUE("ui_issue"),
  OTHER("other");

  companion object {
    fun fromCode(code: String): QuestionReportReason? {
      return entries.find { it.code == code }
    }
  }
}
