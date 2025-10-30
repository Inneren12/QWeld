package com.qweld.app.domain.exam.errors

sealed class ExamAssemblyException(message: String) : RuntimeException(message) {
  data class DeficitDetail(
    val taskId: String,
    val need: Int,
    val have: Int,
    val missing: Int,
    val locale: String,
    val familyDuplicates: Boolean,
  )

  class Deficit(val details: List<DeficitDetail>) :
    ExamAssemblyException(
      buildString {
        append("Exam assembly deficit detected for tasks: ")
        append(
          details.joinToString { detail ->
            "${detail.taskId}(need=${detail.need}, have=${detail.have}, missing=${detail.missing}, locale=${detail.locale}, famDup=${detail.familyDuplicates})"
          }
        )
      }
    )
}
