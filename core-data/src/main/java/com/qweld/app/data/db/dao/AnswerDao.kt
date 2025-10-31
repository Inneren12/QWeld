package com.qweld.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.qweld.app.data.db.entities.AnswerEntity

@Dao
interface AnswerDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(answers: List<AnswerEntity>)

  @Query(
    """
    SELECT *
    FROM answers
    WHERE attempt_id = :attemptId
    ORDER BY display_index ASC
    """
  )
  suspend fun listByAttempt(attemptId: String): List<AnswerEntity>

  @Query(
    """
    SELECT question_id
    FROM answers
    WHERE attempt_id = :attemptId AND is_correct = 0
    ORDER BY display_index ASC
    """,
  )
  suspend fun listWrongByAttempt(attemptId: String): List<String>

  @Query(
    """
    SELECT question_id AS questionId,
           COUNT(*) AS attempts,
           SUM(CASE WHEN is_correct THEN 1 ELSE 0 END) AS correct,
           MAX(answered_at) AS lastAnsweredAt
    FROM answers
    WHERE question_id = :questionId
    GROUP BY question_id
    LIMIT 1
    """
  )
  suspend fun countByQuestion(questionId: String): QuestionAggregate?

  @Query(
    """
    SELECT question_id AS questionId,
           COUNT(*) AS attempts,
           SUM(CASE WHEN is_correct THEN 1 ELSE 0 END) AS correct,
           MAX(answered_at) AS lastAnsweredAt
    FROM answers
    WHERE question_id IN (:questionIds)
    GROUP BY question_id
    """
  )
  suspend fun bulkCountByQuestions(questionIds: List<String>): List<QuestionAggregate>

  @Query("DELETE FROM answers")
  suspend fun clearAll()

  data class QuestionAggregate(
    val questionId: String,
    val attempts: Int,
    val correct: Int,
    val lastAnsweredAt: Long?,
  )
}
