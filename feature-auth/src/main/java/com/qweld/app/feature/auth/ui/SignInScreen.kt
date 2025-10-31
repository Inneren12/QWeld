package com.qweld.app.feature.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.qweld.app.feature.auth.R

@Composable
fun SignInScreen(
  isLoading: Boolean,
  errorMessage: String?,
  onContinueAsGuest: () -> Unit,
  onSignInWithGoogle: () -> Unit,
  onSignInWithEmail: (email: String, password: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var email by rememberSaveable { mutableStateOf("") }
  var password by rememberSaveable { mutableStateOf("") }
  val isFormValid = email.isNotBlank() && password.isNotBlank()

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Top,
  ) {
    Text(
      text = stringResource(id = R.string.auth_sign_in_title),
      style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(modifier = Modifier.height(24.dp))
    if (isLoading) {
      CircularProgressIndicator()
      Spacer(modifier = Modifier.height(16.dp))
    }
    if (!errorMessage.isNullOrBlank()) {
      Text(
        text = errorMessage,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
      )
      Spacer(modifier = Modifier.height(16.dp))
    }
    OutlinedTextField(
      value = email,
      onValueChange = { email = it },
      label = { Text(text = stringResource(id = R.string.auth_email_label)) },
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
      enabled = !isLoading,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
    )
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
      value = password,
      onValueChange = { password = it },
      label = { Text(text = stringResource(id = R.string.auth_password_label)) },
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
      enabled = !isLoading,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
      visualTransformation = PasswordVisualTransformation(),
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(
      onClick = { onSignInWithEmail(email.trim(), password) },
      enabled = !isLoading && isFormValid,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(text = stringResource(id = R.string.auth_sign_in_email_action))
    }
    Spacer(modifier = Modifier.height(24.dp))
    Button(
      onClick = onSignInWithGoogle,
      enabled = !isLoading,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(text = stringResource(id = R.string.auth_sign_in_google_action))
    }
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedButton(
      onClick = onContinueAsGuest,
      enabled = !isLoading,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(text = stringResource(id = R.string.auth_continue_as_guest))
    }
  }
}
