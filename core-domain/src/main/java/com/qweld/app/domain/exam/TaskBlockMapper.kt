package com.qweld.app.domain.exam

private val TASK_TO_BLOCK: Map<String, String> =
  ExamBlueprint.default().taskQuotas.associate { quota -> quota.taskId to quota.blockId }

fun mapTaskToBlock(taskId: String): String? = TASK_TO_BLOCK[taskId]
