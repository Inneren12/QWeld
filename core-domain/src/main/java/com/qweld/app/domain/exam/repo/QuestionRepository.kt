package com.qweld.app.domain.exam.repo

import com.qweld.app.domain.exam.Question

interface QuestionRepository {
  fun listByTaskAndLocale(
    taskId: String,
    locale: String,
    allowFallbackToEnglish: Boolean = false,
  ): List<Question>
}
