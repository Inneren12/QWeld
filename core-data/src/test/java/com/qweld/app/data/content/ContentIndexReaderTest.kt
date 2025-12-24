package com.qweld.app.data.content

import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class ContentIndexReaderTest {

  private class FakeAssetLoader(
    private val assets: Map<String, String>,
    private val listings: Map<String, List<String>> = emptyMap(),
  ) : ContentIndexReader.AssetLoader {
    override fun open(path: String) =
      assets[path]?.let { ByteArrayInputStream(it.toByteArray()) } ?: throw FileNotFoundException(path)

    override fun list(path: String): List<String> = listings[path] ?: emptyList()
  }

  private val json = Json { ignoreUnknownKeys = false }

  @Test
  fun `read parses locale manifest`() {
    val payload =
      """
      {
        "blueprintId": "welder_bp",
        "bankVersion": "v1",
        "files": {
          "questions/en/bank.v1.json": {"sha256": "abc"},
          "questions/en/tasks/A-1.json": {"sha256": "def"}
        }
      }
      """
        .trimIndent()
    val listings = mapOf("questions" to listOf("en"))
    val assets = mapOf("questions/en/index.json" to payload)
    val reader = ContentIndexReader(FakeAssetLoader(assets, listings), json)

    val result = reader.read()

    assertNotNull(result)
    val locale = result.locales.getValue("en")
    assertEquals("welder_bp", locale.blueprintId)
    assertEquals("v1", locale.bankVersion)
    assertEquals(2, locale.files.size)
    assertEquals("questions/en/bank.v1.json", locale.files.first().path)
    assertEquals("abc", locale.files.first().sha256)
    val expectedJson =
      json.parseToJsonElement(
        """
        {
          "en": {
            "blueprintId": "welder_bp",
            "bankVersion": "v1",
            "files": {
              "questions/en/bank.v1.json": {"sha256": "abc"},
              "questions/en/tasks/A-1.json": {"sha256": "def"}
            }
          }
        }
        """
          .trimIndent()
      )
    assertEquals(expectedJson, json.parseToJsonElement(result.rawJson))
  }

  @Test
  fun `read returns null when locale list empty`() {
    val reader = ContentIndexReader(FakeAssetLoader(emptyMap(), emptyMap()), json)

    val result = reader.read()

    assertNull(result)
  }

  @Test
  fun `verify returns empty list when hashes match`() {
    val bankContent = "bank"
    val taskContent = "task"
    val indexPayload =
      """
      {
        "blueprintId": "welder_bp",
        "bankVersion": "v1",
        "files": {
          "questions/en/bank.v1.json": {"sha256": "${sha256(bankContent)}"},
          "questions/en/tasks/A-1.json": {"sha256": "${sha256(taskContent)}"}
        }
      }
      """
        .trimIndent()

    val assets =
      mapOf(
        "questions/en/index.json" to indexPayload,
        "questions/en/bank.v1.json" to bankContent,
        "questions/en/tasks/A-1.json" to taskContent,
      )
    val listings = mapOf("questions" to listOf("en"))
    val reader = ContentIndexReader(FakeAssetLoader(assets, listings), json)

    val result = reader.read()
    val mismatches = reader.verify(result)

    assertNotNull(result)
    assertEquals(emptyList(), mismatches)
  }

  @Test
  fun `verify reports missing assets and hash mismatches`() {
    val bankContent = "bank"
    val taskContent = "task"
    val indexPayload =
      """
      {
        "blueprintId": "welder_bp",
        "bankVersion": "v1",
        "files": {
          "questions/en/bank.v1.json": {"sha256": "${sha256(bankContent)}"},
          "questions/en/tasks/A-1.json": {"sha256": "${sha256(taskContent)}"}
        }
      }
      """
        .trimIndent()

    val assets =
      mapOf(
        "questions/en/index.json" to indexPayload,
        "questions/en/bank.v1.json" to "other",
      )
    val listings = mapOf("questions" to listOf("en"))
    val reader = ContentIndexReader(FakeAssetLoader(assets, listings), json)

    val mismatches = reader.verify(reader.read())

    assertEquals(2, mismatches.size)
    val hashMismatch = mismatches.first { it.path == "questions/en/bank.v1.json" }
    assertEquals(ContentIndexReader.Mismatch.Reason.HASH_MISMATCH, hashMismatch.reason)
    val missingTask = mismatches.first { it.path == "questions/en/tasks/A-1.json" }
    assertEquals(ContentIndexReader.Mismatch.Reason.FILE_MISSING, missingTask.reason)
  }

  @Test
  fun `verify reports missing manifest`() {
    val listings = mapOf("questions" to listOf("en"))
    val reader = ContentIndexReader(FakeAssetLoader(emptyMap(), listings), json)

    val mismatches = reader.verify()

    assertEquals(1, mismatches.size)
    val mismatch = mismatches.single()
    assertEquals("questions/en/index.json", mismatch.path)
    assertEquals(ContentIndexReader.Mismatch.Reason.INDEX_MISSING, mismatch.reason)
  }

  @Test
  fun `read parses real bundled manifests`() {
    val repoRoot = findRepoRoot()
    val assetsRoot = repoRoot.resolve("app-android/src/main/assets").toAbsolutePath().normalize()
    assertTrue(
      Files.isDirectory(assetsRoot),
      "Expected assets directory at: $assetsRoot (repoRoot=$repoRoot, user.dir=${System.getProperty("user.dir")})",
    )

    // Real assets may grow extra metadata keys; keep this test resilient to additive fields.
    val runtimeJson = Json { ignoreUnknownKeys = true }

    val opened = mutableListOf<String>()
    val listed = mutableMapOf<String, List<String>>()

    val loader =
      object : ContentIndexReader.AssetLoader {
          override fun open(path: String) =
              assetsRoot.resolve(path).toFile().inputStream().also { opened.add(path) }

        override fun list(path: String): List<String> {
          // Deterministic order helps keep this test stable across platforms/filesystems.
          val items = assetsRoot.resolve(path).toFile().list()?.toList()?.sorted() ?: emptyList()
          listed[path] = items
          return items
        }
      }

    // 1) Make sure listing("questions") includes the root summary index.json (file), not only locale dirs.
    val questionsListing = loader.list("questions")
    assertTrue(
      questionsListing.contains("index.json"),
      "Expected questions/ listing to contain 'index.json' (root summary). Actual=$questionsListing",
    )

    val reader = ContentIndexReader(loader, runtimeJson)

    val result = reader.read()

    assertNotNull(result)

    // Be tolerant to future locales (fr, etc.) but require at least one parsed locale.
    assertTrue(result.locales.isNotEmpty(), "Expected at least one locale under questions/")

      assertTrue(
          !result.locales.containsKey("index.json"),
          "Root file name 'index.json' must NOT be treated as locale key. locales.keys=${result.locales.keys}",
          )

      // 2) Strong proof: reader must NOT attempt to open 'questions/index.json/index.json'
      // (happens if it mistakenly treats listing entry 'index.json' as a locale directory).
      assertTrue(
          opened.none { it == "questions/index.json/index.json" },
          "Reader attempted to treat questions/index.json as locale directory. opened=$opened listed[questions]=${listed["questions"]}",
          )

    val shaRe = Regex("^[0-9a-fA-F]{64}$")

    // Validate each parsed locale manifest against at least one real file on disk.
    for ((locale, manifest) in result.locales.toSortedMap()) {
        assertTrue(
            !manifest.blueprintId.isNullOrBlank(),
            "blueprintId is blank for locale=$locale (value='${manifest.blueprintId}')",
            )
        assertTrue(
            !manifest.bankVersion.isNullOrBlank(),
            "bankVersion is blank for locale=$locale (value='${manifest.bankVersion}')",
            )

      val entries =
        manifest.files
          .filterNot { it.path.endsWith("/index.json") }
          .sortedBy { it.path }
      assertTrue(entries.isNotEmpty(), "Manifest for locale=$locale has no file entries")

      // Pick a sample entry that exists on disk (supports optional .gz toggling).
      val sample =
        entries.firstOrNull { e ->
          Files.isRegularFile(assetsRoot.resolve(e.path)) ||
            Files.isRegularFile(assetsRoot.resolve(toggleGz(e.path)))
        } ?: entries.first()

      val expectedSha = sample.sha256
      assertTrue(shaRe.matches(expectedSha), "Invalid sha256 format for ${sample.path}: '$expectedSha'")

      val candidatePaths = listOf(sample.path, toggleGz(sample.path)).distinct()
      val candidateFiles =
        candidatePaths
          .map { assetsRoot.resolve(it) }
          .filter { Files.isRegularFile(it) }

      assertTrue(
        candidateFiles.isNotEmpty(),
        "No file exists on disk for locale=$locale entry=${sample.path} (candidates=$candidatePaths, assetsRoot=$assetsRoot)",
      )

      val expectedNorm = expectedSha.lowercase()
      val actualCandidates =
        candidateFiles
          .map { sha256Bytes(Files.readAllBytes(it)).lowercase() }
          .toSet()

      assertTrue(
        actualCandidates.contains(expectedNorm),
        "SHA-256 mismatch for locale=$locale entry=${sample.path}. expected=$expectedNorm actual=$actualCandidates candidates=$candidatePaths",
      )
    }
  }

  private fun sha256(content: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(content.toByteArray())
    return bytes.joinToString(separator = "") { "%02x".format(it) }
  }

  private fun sha256Bytes(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val out = digest.digest(bytes)
    return out.joinToString(separator = "") { "%02x".format(it) }
  }

  private fun findRepoRoot(): Path {
    val start = Paths.get("").toAbsolutePath().normalize()
    return generateSequence(start) { it.parent }
      .firstOrNull { p ->
        Files.exists(p.resolve("settings.gradle.kts")) ||
          Files.exists(p.resolve("settings.gradle")) ||
          Files.exists(p.resolve("gradlew")) ||
          Files.exists(p.resolve(".git"))
      } ?: start
  }

  private fun toggleGz(path: String): String =
    if (path.endsWith(".gz")) path.removeSuffix(".gz") else "$path.gz"
}
