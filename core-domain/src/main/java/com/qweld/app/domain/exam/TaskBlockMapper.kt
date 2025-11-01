package com.qweld.app.domain.exam

fun mapTaskToBlock(taskId: String): String? {
  val prefix = taskId.firstOrNull { it.isLetter() }?.uppercaseChar() ?: return null
  return when (prefix) {
    'A', 'B', 'C', 'D' -> prefix.toString()
    else -> null
  }
}
