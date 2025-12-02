package com.qweld.app.feature.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import timber.log.Timber

/**
 * Manager for handling Google Sign-In using Credential Manager API.
 *
 * This replaces the deprecated GoogleSignInClient flow with the modern
 * Credential Manager approach.
 */
interface GoogleCredentialSignInManager {
  /**
   * Initiates Google Sign-In flow using Credential Manager.
   *
   * @param context Android context for credential manager operations
   * @return Result containing the Google ID token on success, or an error on failure
   */
  suspend fun signInWithGoogleIdToken(context: Context): Result<String>
}

/**
 * Default implementation of GoogleCredentialSignInManager using Credential Manager API.
 *
 * @param serverClientId The OAuth 2.0 web client ID for your server. This is typically
 *                       retrieved from R.string.default_web_client_id which is auto-generated
 *                       from google-services.json
 */
class DefaultGoogleCredentialSignInManager(
  private val serverClientId: String,
) : GoogleCredentialSignInManager {

  override suspend fun signInWithGoogleIdToken(context: Context): Result<String> {
    return try {
      val credentialManager = CredentialManager.create(context)

      // Build the Google ID option
      val googleIdOption =
        GetGoogleIdOption.Builder()
          .setServerClientId(serverClientId)
          .setFilterByAuthorizedAccounts(true)
          .build()

      // Build the credential request
      val request =
        GetCredentialRequest.Builder()
          .addCredentialOption(googleIdOption)
          .build()

      // Get the credential from Credential Manager
      val result = credentialManager.getCredential(context = context, request = request)

      // Extract and return the ID token
      extractIdToken(result)
    } catch (e: GetCredentialCancellationException) {
      Timber.d("User cancelled the Google Sign-In flow")
      Result.failure(GoogleSignInCancelledException("User cancelled sign-in", e))
    } catch (e: NoCredentialException) {
      Timber.w(e, "No Google credentials available - user may need to add a Google account")
      // When no authorized accounts are found, retry without the filter
      tryWithoutAccountFilter(context)
    } catch (e: GetCredentialException) {
      Timber.e(e, "Failed to get Google credential")
      Result.failure(GoogleSignInException("Failed to get Google credential: ${e.message}", e))
    } catch (e: GoogleIdTokenParsingException) {
      Timber.e(e, "Failed to parse Google ID token")
      Result.failure(GoogleSignInException("Failed to parse Google ID token: ${e.message}", e))
    } catch (e: Exception) {
      Timber.e(e, "Unexpected error during Google Sign-In")
      Result.failure(GoogleSignInException("Unexpected error: ${e.message}", e))
    }
  }

  /**
   * Retry credential request without filtering by authorized accounts.
   * This allows the user to add a new Google account if they don't have one yet.
   */
  private suspend fun tryWithoutAccountFilter(context: Context): Result<String> {
    return try {
      Timber.d("Retrying Google Sign-In without account filter")
      val credentialManager = CredentialManager.create(context)

      val googleIdOption =
        GetGoogleIdOption.Builder()
          .setServerClientId(serverClientId)
          .setFilterByAuthorizedAccounts(false)
          .build()

      val request =
        GetCredentialRequest.Builder()
          .addCredentialOption(googleIdOption)
          .build()

      val result = credentialManager.getCredential(context = context, request = request)
      extractIdToken(result)
    } catch (e: GetCredentialCancellationException) {
      Timber.d("User cancelled the Google Sign-In flow (retry)")
      Result.failure(GoogleSignInCancelledException("User cancelled sign-in", e))
    } catch (e: GetCredentialException) {
      Timber.e(e, "Failed to get Google credential (retry)")
      Result.failure(GoogleSignInException("Failed to get Google credential: ${e.message}", e))
    } catch (e: Exception) {
      Timber.e(e, "Unexpected error during Google Sign-In (retry)")
      Result.failure(GoogleSignInException("Unexpected error: ${e.message}", e))
    }
  }

  /**
   * Extracts the Google ID token from the credential response.
   */
  private fun extractIdToken(response: GetCredentialResponse): Result<String> {
    val credential = response.credential

    if (credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
      val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
      val idToken = googleIdTokenCredential.idToken

      if (idToken.isNotEmpty()) {
        Timber.d("Successfully obtained Google ID token")
        return Result.success(idToken)
      } else {
        Timber.e("Google ID token is empty")
        return Result.failure(GoogleSignInException("Google ID token is empty"))
      }
    } else {
      Timber.e("Received unexpected credential type: ${credential.type}")
      return Result.failure(
          GoogleSignInException("Unexpected credential type: ${credential.type}"))
    }
  }
}

/**
 * Exception thrown when Google Sign-In fails.
 */
class GoogleSignInException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Exception thrown when user cancels Google Sign-In.
 */
class GoogleSignInCancelledException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
