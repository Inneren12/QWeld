package com.qweld.app.domain.exam.repo

import com.qweld.app.domain.exam.Question

interface QuestionRepository {
    fun loadQuestions(
        taskId: String,
        locale: String,
        allowFallbackToEnglish: Boolean,
    ): List<Question>
}
