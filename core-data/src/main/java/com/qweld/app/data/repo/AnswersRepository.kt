package com.qweld.app.data.repo

import com.qweld.app.data.db.dao.AnswerDao
import com.qweld.app.data.db.entities.AnswerEntity

interface AnswersRepository {
  suspend fun upsert(answers: List<AnswerEntity>)
  suspend fun listByAttempt(attemptId: String): List<AnswerEntity>
  suspend fun listWrongByAttempt(attemptId: String): List<String>
  suspend fun countByQuestion(questionId: String): AnswerDao.QuestionAggregate?
  suspend fun bulkCountByQuestions(questionIds: List<String>): List<AnswerDao.QuestionAggregate>
  suspend fun countAll(): Int
  suspend fun clearAll()
}

class DefaultAnswersRepository(private val answerDao: AnswerDao) : AnswersRepository {

  override suspend fun upsert(answers: List<AnswerEntity>) {
    if (answers.isEmpty()) return
    answerDao.insertAll(answers)
  }

  override suspend fun listByAttempt(attemptId: String): List<AnswerEntity> = answerDao.listByAttempt(attemptId)

  override suspend fun listWrongByAttempt(attemptId: String): List<String> = answerDao.listWrongByAttempt(attemptId)

  override suspend fun countByQuestion(questionId: String): AnswerDao.QuestionAggregate? =
    answerDao.countByQuestion(questionId)

  override suspend fun bulkCountByQuestions(questionIds: List<String>): List<AnswerDao.QuestionAggregate> =
    answerDao.bulkCountByQuestions(questionIds)

  override suspend fun countAll(): Int = answerDao.countAll()

  override suspend fun clearAll() {
    answerDao.clearAll()
  }
}
