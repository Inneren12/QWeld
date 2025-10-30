package com.qweld.app.domain.exam

import com.qweld.app.domain.exam.errors.ExamAssemblyException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class LocaleDeficitTest {
    private val blueprint = ExamBlueprint(
        totalQuestions = 2,
        taskQuotas = listOf(
            TaskQuota("A-1", "A", 1),
            TaskQuota("B-1", "B", 1),
        ),
    )

    @Test
    fun `ip mock blocks when locale pool missing`() {
        val questions =
            generateQuestions("A-1", 2, locale = "RU") +
                generateQuestions("B-1", 2, locale = "EN")
        val repo = FakeQuestionRepository(questions)
        val assembler = ExamAssembler(
            questionRepository = repo,
            statsRepository = FakeUserStatsRepository(),
            clock = fixedClock(),
        )

        val error = assertFailsWith<ExamAssemblyException.Deficit> {
            assembler.assemble(
                userId = "user",
                mode = ExamMode.IP_MOCK,
                locale = "RU",
                seed = AttemptSeed(5L),
                blueprint = blueprint,
            )
        }

        val detail = error.details.single { it.taskId == "B-1" }
        assertEquals("RU", detail.locale)
        assertEquals(1, detail.need)
        assertEquals(0, detail.have)
        assertEquals(1, detail.missing)
    }
}
