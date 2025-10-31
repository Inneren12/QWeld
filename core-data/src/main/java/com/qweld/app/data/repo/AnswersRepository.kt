package com.qweld.app.data.repo

import com.qweld.app.data.db.dao.AnswerDao
import com.qweld.app.data.db.entities.AnswerEntity

class AnswersRepository(private val answerDao: AnswerDao) {
  suspend fun saveAll(answers: List<AnswerEntity>) {
    if (answers.isEmpty()) return
    answerDao.insertAll(answers)
  }

  suspend fun listByAttempt(attemptId: String): List<AnswerEntity> = answerDao.listByAttempt(attemptId)

  suspend fun countByQuestion(questionId: String): AnswerDao.QuestionAggregate? =
    answerDao.countByQuestion(questionId)

  suspend fun bulkCountByQuestions(questionIds: List<String>): List<AnswerDao.QuestionAggregate> =
    answerDao.bulkCountByQuestions(questionIds)
}
