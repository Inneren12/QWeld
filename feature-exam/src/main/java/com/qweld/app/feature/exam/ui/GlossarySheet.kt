package com.qweld.app.feature.exam.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.qweld.app.feature.exam.R
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Composable
fun GlossarySheet(
  entries: List<GlossaryEntry>,
  isLoading: Boolean,
  hasError: Boolean,
  modifier: Modifier = Modifier,
) {
  val clipboardManager = LocalClipboardManager.current
  val context = LocalContext.current
  var query by rememberSaveable { mutableStateOf("") }
  val filtered = remember(entries, query) {
    if (query.isBlank()) {
      entries
    } else {
      val normalized = query.trim().lowercase(Locale.getDefault())
      entries.filter { entry ->
        entry.term.lowercase(Locale.getDefault()).contains(normalized) ||
          entry.translation.lowercase(Locale.getDefault()).contains(normalized)
      }
    }
  }
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text(
      text = stringResource(id = R.string.review_glossary_title),
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.SemiBold,
    )
    OutlinedTextField(
      modifier = Modifier.fillMaxWidth(),
      value = query,
      onValueChange = { query = it },
      placeholder = { Text(text = stringResource(id = R.string.review_glossary_search_placeholder)) },
      singleLine = true,
      shape = RoundedCornerShape(12.dp),
      keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
      trailingIcon = {
        if (query.isNotEmpty()) {
          IconButton(onClick = { query = "" }) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = stringResource(id = R.string.review_search_clear_cd),
            )
          }
        }
      },
    )
    when {
      isLoading -> {
        Spacer(modifier = Modifier.height(16.dp))
        CircularProgressIndicator()
      }
      hasError -> {
        Text(
          text = stringResource(id = R.string.review_glossary_error),
          style = MaterialTheme.typography.bodyMedium,
        )
      }
      filtered.isEmpty() -> {
        Text(
          text = stringResource(id = R.string.review_glossary_empty),
          style = MaterialTheme.typography.bodyMedium,
        )
      }
      else -> {
        LazyColumn(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(filtered) { entry ->
            GlossaryRow(
              entry = entry,
              query = query,
              onClick = {
                clipboardManager.setText(AnnotatedString("${entry.term} — ${entry.translation}"))
                Toast.makeText(
                  context,
                  context.getString(R.string.review_glossary_copied, entry.term, entry.translation),
                  Toast.LENGTH_SHORT,
                ).show()
              },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun GlossaryRow(
  entry: GlossaryEntry,
  query: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val highlightColor = MaterialTheme.colorScheme.secondaryContainer
  val onSurface = MaterialTheme.colorScheme.onSurface
  val highlightStyle = remember(onSurface, highlightColor) {
    SpanStyle(
      background = highlightColor.copy(alpha = 0.4f),
      fontWeight = FontWeight.SemiBold,
      color = onSurface,
    )
  }
  val termText = remember(entry.term, query, highlightStyle) {
    highlightGlossaryText(entry.term, query, highlightStyle)
  }
  val translationText = remember(entry.translation, query, highlightStyle) {
    highlightGlossaryText(entry.translation, query, highlightStyle)
  }
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(12.dp),
    tonalElevation = 1.dp,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(text = termText, style = MaterialTheme.typography.titleMedium)
      Text(text = translationText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

private fun highlightGlossaryText(
  text: String,
  query: String,
  highlightStyle: SpanStyle,
): AnnotatedString {
  if (query.isBlank() || text.isEmpty()) return AnnotatedString(text)
  val lowerText = text.lowercase(Locale.getDefault())
  val lowerQuery = query.trim().lowercase(Locale.getDefault())
  // Стиль передаётся снаружи, внутри нет обращения к MaterialTheme
  val ranges = buildList {
    var start = 0
    while (true) {
      val idx = lowerText.indexOf(lowerQuery, startIndex = start)
      if (idx < 0) break
      add(idx until (idx + lowerQuery.length))
      start = idx + lowerQuery.length
    }
  }
  if (ranges.isEmpty()) return AnnotatedString(text)
  return buildAnnotatedString {
    append(text)
    for (r in ranges) {
      addStyle(highlightStyle, r.first, r.last + 1)
    }
  }
}

internal fun loadGlossaryFromAssets(
  context: Context,
  locale: Locale,
  json: Json = defaultGlossaryJson,
): List<GlossaryEntry> {
  val language = locale.language.lowercase(Locale.US)
  val candidates = listOf(
    "docs/glossary/glossary_${language}.json",
    "docs/glossary/glossary_ru.json",
  ).distinct()
  for (path in candidates) {
    val stream = runCatching { context.assets.open(path) }.getOrNull() ?: continue
    val payload = stream.use { input -> input.bufferedReader().use { it.readText() } }
    val parsed = parseGlossaryJson(payload, json)
    if (parsed.isNotEmpty()) {
      return parsed
    }
  }
  return emptyList()
}

internal fun parseGlossaryJson(payload: String, json: Json = defaultGlossaryJson): List<GlossaryEntry> {
  val dto = json.decodeFromString(GlossaryWrapper.serializer(), payload)
  return dto.entries.map { GlossaryEntry(term = it.en.trim(), translation = it.ru.trim()) }
    .filter { it.term.isNotEmpty() && it.translation.isNotEmpty() }
}

private val defaultGlossaryJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class GlossaryWrapper(
  val entries: List<GlossaryItemDto>,
)

@Serializable
private data class GlossaryItemDto(
  val en: String,
  val ru: String,
)

data class GlossaryEntry(
  val term: String,
  val translation: String,
)
