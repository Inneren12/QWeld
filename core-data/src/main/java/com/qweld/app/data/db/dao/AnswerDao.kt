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
    SELECT a.question_id AS questionId,
           COUNT(*) AS attempts,
           SUM(CASE WHEN a.is_correct THEN 1 ELSE 0 END) AS correct,
           MAX(a.answered_at) AS lastAnsweredAt,
           (
             SELECT CASE WHEN last.is_correct THEN 1 ELSE 0 END
             FROM answers AS last
             WHERE last.question_id = a.question_id
             ORDER BY last.answered_at DESC
             LIMIT 1
           ) AS lastIsCorrect
    FROM answers AS a
    WHERE a.question_id = :questionId
    GROUP BY a.question_id
    LIMIT 1
    """
  )
  suspend fun countByQuestion(questionId: String): QuestionAggregate?

  @Query(
    """
    SELECT a.question_id AS questionId,
           COUNT(*) AS attempts,
           SUM(CASE WHEN a.is_correct THEN 1 ELSE 0 END) AS correct,
           MAX(a.answered_at) AS lastAnsweredAt,
           (
             SELECT CASE WHEN last.is_correct THEN 1 ELSE 0 END
             FROM answers AS last
             WHERE last.question_id = a.question_id
             ORDER BY last.answered_at DESC
             LIMIT 1
           ) AS lastIsCorrect
    FROM answers AS a
    WHERE a.question_id IN (:questionIds)
    GROUP BY a.question_id
    """
  )
  suspend fun bulkCountByQuestions(questionIds: List<String>): List<QuestionAggregate>

  @Query("DELETE FROM answers")
  suspend fun clearAll()

  @Query("SELECT COUNT(*) FROM answers")
  suspend fun countAll(): Int

  data class QuestionAggregate(
    val questionId: String,
    val attempts: Int,
    val correct: Int,
    val lastAnsweredAt: Long?,
    val lastIsCorrect: Boolean?,
  )
}
