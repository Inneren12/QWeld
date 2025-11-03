package com.qweld.benchmarks

import com.qweld.app.domain.Outcome
import com.qweld.app.domain.exam.AttemptSeed
import com.qweld.app.domain.exam.ExamAssembler
import com.qweld.app.domain.exam.ExamBlueprint
import com.qweld.app.domain.exam.ExamMode
import com.qweld.app.domain.exam.ItemStats
import com.qweld.app.domain.exam.Question
import com.qweld.app.domain.exam.TaskQuota
import com.qweld.app.domain.exam.repo.QuestionRepository
import com.qweld.app.domain.exam.repo.UserStatsRepository
import java.io.InputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
open class ExamAssemblyBenchmark {
  private val json = Json { ignoreUnknownKeys = true }
  private lateinit var blueprint: ExamBlueprint
  private lateinit var repository: FixtureQuestionRepository
  private lateinit var assembler: ExamAssembler
  private lateinit var locale: String

  @Setup(Level.Trial)
  fun setUp() {
    val loader = FixtureLoader(json)
    blueprint = loader.loadBlueprint("fixtures/blueprint.json")
    val questions = loader.loadQuestions("fixtures/question-bank.json")
    locale = loader.locale
    repository = FixtureQuestionRepository(questions)
    assembler =
      ExamAssembler(
        questionRepository = repository,
        statsRepository = EmptyStatsRepository,
        clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC),
        logger = {},
      )
  }

  @Benchmark
  fun assembleExam(blackhole: Blackhole) {
    val result = runBlocking {
      assembler.assemble(
        userId = "benchmark",
        mode = ExamMode.IP_MOCK,
        locale = locale,
        seed = AttemptSeed(42L),
        blueprint = blueprint,
      )
    }
    when (result) {
      is Outcome.Ok -> blackhole.consume(result.value.exam)
      is Outcome.Err.QuotaExceeded ->
        error(
          "Deficit for ${result.taskId}: required=${result.required} have=${result.have}",
        )
      is Outcome.Err -> error("Unexpected outcome ${result::class.simpleName}")
    }
  }
}

private object EmptyStatsRepository : UserStatsRepository {
  override suspend fun getUserItemStats(
    userId: String,
    ids: List<String>,
  ): Outcome<Map<String, ItemStats>> = Outcome.Ok(emptyMap())
}

private class FixtureQuestionRepository(
  questions: List<Question>,
) : QuestionRepository {
  private val byTask =
    questions.groupBy { it.taskId }.mapValues { (_, list) -> list.sortedBy { it.id } }

  override fun listByTaskAndLocale(
    taskId: String,
    locale: String,
    allowFallbackToEnglish: Boolean,
  ): Outcome<List<Question>> {
    val normalized = locale.lowercase(Locale.US)
    val candidates = byTask[taskId].orEmpty()
    val primary = candidates.filter { it.locale.lowercase(Locale.US) == normalized }
    if (primary.isNotEmpty() || !allowFallbackToEnglish) {
      return Outcome.Ok(primary)
    }
    return Outcome.Ok(candidates.filter { it.locale.equals("en", ignoreCase = true) })
  }
}

private class FixtureLoader(private val json: Json) {
  lateinit var locale: String
    private set

  fun loadBlueprint(path: String): ExamBlueprint {
    val spec =
      loadResource(path) { stream ->
        json.decodeFromString(BlueprintSpec.serializer(), stream.readText())
      }
    val quotas =
      spec.blocks.flatMap { block ->
        block.tasks.map { task ->
          TaskQuota(taskId = task.id, blockId = block.id, required = task.quota)
        }
      }
    return ExamBlueprint(totalQuestions = spec.questionCount, taskQuotas = quotas)
  }

  fun loadQuestions(path: String): List<Question> {
    val spec =
      loadResource(path) { stream ->
        json.decodeFromString(QuestionBankSpec.serializer(), stream.readText())
      }
    locale = spec.locale.ifBlank { "en" }.lowercase(Locale.US)
    return spec.tasks.flatMap { task ->
      val block = task.blockId ?: task.taskId.substringBefore("-")
      (0 until task.count).map { index ->
        val id = "%s-%s-%03d".format(task.taskId, locale.uppercase(Locale.US), index)
        Question(
          id = id,
          taskId = task.taskId,
          blockId = block,
          locale = locale,
          stem = mapOf(locale to "Question $id"),
          familyId = "${task.taskId}-fam-$index",
          choices =
            listOf("A", "B", "C", "D").map { label ->
              com.qweld.app.domain.exam.Choice(
                id = "$id-$label",
                text = mapOf(locale to "Choice $label"),
              )
            },
          correctChoiceId = "$id-A",
        )
      }
    }
  }

  private inline fun <T> loadResource(path: String, reader: (InputStream) -> T): T {
    val stream =
      requireNotNull(javaClass.classLoader?.getResourceAsStream(path)) { "Missing resource: $path" }
    return stream.use(reader)
  }

  private fun InputStream.readText(): String = bufferedReader().use { it.readText() }
}

@Serializable
private data class BlueprintSpec(
  val questionCount: Int,
  val blocks: List<BlockSpec> = emptyList(),
)

@Serializable
private data class BlockSpec(
  val id: String,
  val tasks: List<TaskSpec> = emptyList(),
)

@Serializable
private data class TaskSpec(
  val id: String,
  val quota: Int,
)

@Serializable
private data class QuestionBankSpec(
  val locale: String = "en",
  val tasks: List<QuestionTaskSpec> = emptyList(),
)

@Serializable
private data class QuestionTaskSpec(
  val taskId: String,
  val blockId: String? = null,
  val count: Int,
)
