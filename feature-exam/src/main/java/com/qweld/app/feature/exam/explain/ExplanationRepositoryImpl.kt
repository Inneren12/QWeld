package com.qweld.app.feature.exam.explain

import com.qweld.app.feature.exam.data.AssetExplanationRepository
import java.util.Locale

internal class ExplanationRepositoryImpl {

  private val explanations = mutableMapOf<String, CachedExplanation>()

  fun put(questionId: String, explanation: AssetExplanationRepository.Explanation?) {
    if (explanation == null) {
      explanations.remove(questionId)
      return
    }
    explanations[questionId] = CachedExplanation.from(explanation)
  }

  fun search(query: String): Set<String> {
    if (query.isBlank()) return emptySet()
    val normalized = query.trim().lowercase(Locale.getDefault())
    val hits = mutableSetOf<String>()
    for ((questionId, cached) in explanations) {
      if (cached.matches(normalized)) {
        hits += questionId
      }
    }
    return hits
  }

  private data class CachedExplanation(
    private val segments: List<String>,
  ) {
    fun matches(query: String): Boolean {
      for (segment in segments) {
        if (segment.contains(query)) return true
      }
      return false
    }

    companion object {
      fun from(explanation: AssetExplanationRepository.Explanation): CachedExplanation {
        val normalizedSegments = buildList {
          explanation.summary?.let { add(it) }
          explanation.steps.forEach { step ->
            step.title?.let { add(it) }
            step.text?.let { add(it) }
          }
          explanation.whyNot.forEach { reason ->
            reason.choiceId?.let { add(it) }
            reason.text?.let { add(it) }
          }
          addAll(explanation.tips)
        }.map { it.trim().lowercase(Locale.getDefault()) }.filter { it.isNotEmpty() }
        return CachedExplanation(normalizedSegments)
      }
    }
  }
}
