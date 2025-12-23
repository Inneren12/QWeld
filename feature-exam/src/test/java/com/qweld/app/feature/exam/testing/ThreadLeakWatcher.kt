package com.qweld.app.feature.exam.testing

import org.junit.rules.TestWatcher
import org.junit.runner.Description

class ThreadLeakWatcher(
    private val printStacks: Boolean = true,
    ) : TestWatcher() {
        private val baselineNames = setOf(
            "main",
            "Test worker",
            "Reference Handler",
            "Finalizer",
            "Signal Dispatcher",
            "Attach Listener",
            "Common-Cleaner",
            )

      private fun suspiciousNonDaemonThreads(): List<Thread> =
          Thread.getAllStackTraces().keys.filter { it.isAlive && !it.isDaemon }
          .filterNot { it.name in baselineNames }
          .sortedBy { it.name }

    private var beforeIds: Set<Long> = emptySet()

    override fun starting(description: Description) {
        beforeIds = suspiciousNonDaemonThreads().map { it.id }.toSet()
    }

    override fun finished(description: Description) {
        val after = suspiciousNonDaemonThreads()
        val leaked = after.filter { it.id !in beforeIds }
        if (leaked.isEmpty()) return

        println("⚠️ [thread-leak] ${description.className}#${description.methodName}: +${leaked.size} non-daemon threads")
        val stacks = Thread.getAllStackTraces()
        for (t in leaked) {
            println("  - ${t.name} id=${t.id} state=${t.state}")
            if (printStacks) {
                stacks[t]?.take(30)?.forEach { println("      at $it") }
            }
        }
    }
    }
