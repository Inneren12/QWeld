package com.qweld.app.feature.exam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.qweld.app.feature.exam.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close

@Composable
fun ReviewSearchBar(
  query: String,
  hits: Int,
  onQueryChange: (String) -> Unit,
  onClear: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    OutlinedTextField(
      modifier = Modifier.fillMaxWidth(),
      value = query,
      onValueChange = onQueryChange,
      placeholder = { Text(text = stringResource(id = R.string.review_search_placeholder)) },
      singleLine = true,
      shape = RoundedCornerShape(12.dp),
      keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
      trailingIcon = {
        if (query.isNotEmpty()) {
          IconButton(onClick = onClear) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = stringResource(id = R.string.review_search_clear_cd),
              modifier = Modifier.size(20.dp),
            )
          }
        }
      },
    )
    if (query.isNotBlank()) {
      val text = if (hits > 0) {
        stringResource(id = R.string.review_search_hits, hits)
      } else {
        stringResource(id = R.string.review_search_no_hits)
      }
      Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
      )
    }
  }
}
